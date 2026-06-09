"""
Plan Generation Service

Builds and executes the planning-only LangGraph subgraph defined in plan_agent.py.
Responsibilities:
  - Image severity assessment (non-blocking)
  - Build the disease-profile-aware retrieval query and generation prompt
  - Invoke the plan_agent graph and return structured result dicts
"""

import logging
import uuid
from typing import Any, Dict, List, Optional

from langchain_core.messages import HumanMessage

from app.agents.rag_state import GraphState
import app.agents.plan_agent as plan_agent_module
from app.agents.plan_agent import (
    resolve_profile,
    build_question,
    SUPPORTED_DISEASES,
    DISEASE_POLICIES,
    get_policy,
    _find_policy_key,
)
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode

logger = logging.getLogger(__name__)

_TYPE_RETRIEVAL_KW = {
    "fungal": "điều trị phòng trừ nấm thuốc trừ nấm liều lượng quy trình phun phác đồ",
    "insect": "phòng trừ sâu thuốc trừ sâu mật độ ngưỡng kinh tế lịch phun",
    "mite": "phòng trừ nhện acaricide thuốc diệt nhện phun mặt dưới lá luân phiên",
    "unknown": "điều trị phòng trừ thuốc bảo vệ thực vật liều lượng quy trình phun",
}


class PlanGenerationService:
    """
    Service layer for standalone plan generation.

    Responsibilities:
    - Image severity assessment (non-blocking)
    - Build the disease-profile-aware retrieval query and generation prompt
    - Produce structured result dicts for controllers
    """

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            instance = super().__new__(cls)
            cls._instance = instance
        return cls._instance

    def _build_initial_state(
        self,
        question: str,
        language: str,
        user_id: Optional[str],
        auth_header: Optional[str],
        plant_id: Optional[str],
        farm_plot_id: Optional[str],
        farm_zone_id: Optional[str],
        search_query: str,
    ) -> GraphState:
        """Create a clean per-request graph state payload."""
        return {
            # Core fields
            "messages": [HumanMessage(content=question)],
            "question": question,
            "generation": "",
            "documents": [],
            "retry_count": 0,
            "language": language,
            "user_id": user_id,
            "authorization": auth_header,
            # IoT context
            "plant_id": plant_id or None,
            "farm_plot_id": farm_plot_id or None,
            "farm_zone_id": farm_zone_id or None,
            # Routing — force planning path
            "forced_route": "planning",
            "path_type": "planning",
            "search_query": search_query or None,
            # Intent / clarification (required by GraphState but unused in this subgraph)
            "intent": "agriculture_query",
            "needs_clarification": False,
            "clarification_question": "",
            "original_question": question,
            # Routing metadata (present for completeness)
            "confidence_score": None,
            "completeness_score": None,
            # Safety (initialized pass-through; safety_auditor sets actual values)
            "safety_passed": True,
            "safety_issues": [],
            "refinement_count": 0,
            "refinement_guidance": "",
            "regulatory_flags": [],
            # Planning output
            "generated_plan": None,
            "summary": None,
            # IoT env state (set by env_state node)
            "env_state": {},
            # Web search (populated by web_search_plan node)
            "web_search_results": [],
        }

    def _build_result(self, final_state: GraphState, disease_name: str) -> Dict[str, Any]:
        """Build the plan generation result dict from the final graph state."""
        documents = final_state.get("documents") or []
        web_results: List[Dict[str, Any]] = final_state.get("web_search_results") or []
        plan = final_state.get("generated_plan") or {}
        refinement_count = final_state.get("refinement_count", 0)
        safety_passed = final_state.get("safety_passed", True)

        best_rerank_score = 0.0
        if documents:
            best_rerank_score = max(
                (doc.metadata.get("rerank_score", 0.0) for doc in documents),
                default=0.0,
            )

        # Resolve disease type and policy
        profile = resolve_profile(disease_name)
        disease_type = profile.get("type", "unknown")
        policy = get_policy(disease_name)

        return {
            "plan": plan,
            "documents": [
                {
                    "title": doc.metadata.get("title", ""),
                    "content": doc.page_content,
                    "url": doc.metadata.get("url"),
                    "score": doc.metadata.get("rerank_score", 0.0),
                }
                for doc in documents
            ],
            "web_sources": [
                {
                    "title": r.get("title", ""),
                    "content": r.get("content", ""),
                    "url": r.get("url"),
                    "score": r.get("score", 0.0),
                }
                for r in web_results
            ],
            "metadata": {
                "best_rerank_score": best_rerank_score,
                "web_sources_count": len(web_results),
                "refinement_attempts": refinement_count,
                "safety_passed": safety_passed,
                "disease_type": disease_type,
                "web_search_used": True,
                # Policy fields (null if disease not in DISEASE_POLICIES)
                "policy_key": _find_policy_key(disease_name) if policy else None,
                "preferred_spray_interval_days": (
                    policy["preferred_spray_interval_days"] if policy else None
                ),
                "frac_rotation_required": policy.get("frac_rotation_required") if policy else None,
                "humidity_sensitive": policy.get("humidity_sensitive") if policy else None,
            },
        }

    def _get_graph(self):
        """Return the live plan_app, raising AppException if not yet initialised."""
        if plan_agent_module.plan_app is None:
            raise AppException(
                ErrorCode.RAG_PIPELINE_ERROR,
                "Plan graph is not initialised yet — service may still be starting up.",
            )
        return plan_agent_module.plan_app

    async def generate_plan(
        self,
        disease_name: str,
        language: str,
        image_url: Optional[str],
        include_web_search: bool,
        user_id: Optional[str] = None,
        auth_header: Optional[str] = None,
        plant_id: Optional[str] = None,
        farm_plot_id: Optional[str] = None,
        farm_zone_id: Optional[str] = None,
        severity_level: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Execute the planning subgraph and return structured plan + metadata.

        Args:
            disease_name:        Name of the detected disease (must be one of SUPPORTED_DISEASES).
            language:            Output language for the plan.
            image_url:           Optional image URL for severity assessment.
            include_web_search:  If False, web_search_plan is never invoked
                                 (check_doc_quality still runs; low doc score will
                                 trigger web search regardless).
            user_id / auth_header: Auth context for downstream service calls.
            plant_id / farm_plot_id / farm_zone_id: IoT context seeding.

        Returns:
            Dict with keys: plan, documents, web_sources, metadata

        Raises:
            AppException(RAG_PIPELINE_ERROR): if disease_name is not in SUPPORTED_DISEASES.
        """
        # ── Validate supported diseases ─────────────────────────────────────────
        if disease_name.lower().replace("_", " ").strip() not in [
            d.lower() for d in SUPPORTED_DISEASES
        ]:
            profile = resolve_profile(disease_name)
            if profile.get("type") == "unknown":
                raise AppException(
                    ErrorCode.RAG_PIPELINE_ERROR,
                    f"Bệnh '{disease_name}' chưa được hỗ trợ. "
                    f"Các bệnh hiện được hỗ trợ: {', '.join(SUPPORTED_DISEASES)}. "
                    f"Vui lòng chọn một bệnh từ danh sách được hỗ trợ.",
                )
        # 1. Severity assessment (non-blocking)
        severity_note = ""
        if image_url:
            try:
                from app.services.image_assessment_service import get_image_assessment_service
                svc = get_image_assessment_service()
                assessment = await svc.assess_image(image_url, disease_name)
                if assessment:
                    severity_note = assessment
                    logger.info("[PLAN SVC] Image severity assessed: %s", severity_note[:120])
            except Exception as exc:
                logger.warning("[PLAN SVC] Image assessment failed (non-fatal): %s", exc)

        # 2. Disease profile & query construction
        profile = resolve_profile(disease_name)
        disease_type = profile.get("type", "unknown")

        viet_search_query = " ".join([
            profile["vn"],
            "cà phê Việt Nam Tây Nguyên",
            _TYPE_RETRIEVAL_KW[disease_type],
            "kế hoạch chăm sóc kỹ thuật thời gian cách ly PHI",
            profile["web_kw"],
        ])

        question = build_question(disease_name, profile, severity_note, severity_level, language)

        # 3. Initial graph state
        initial_state = self._build_initial_state(
            question=question,
            language=language,
            user_id=user_id,
            auth_header=auth_header,
            plant_id=plant_id,
            farm_plot_id=farm_plot_id,
            farm_zone_id=farm_zone_id,
            search_query=viet_search_query,
        )

        # 4. Invoke subgraph
        thread_id = f"plan-{uuid.uuid4()}"
        config = {"configurable": {"thread_id": thread_id}, "recursion_limit": 50}

        logger.info(
            "[PLAN SVC] disease=%s | type=%s | include_web_search=%s | thread=%s",
            disease_name, disease_type, include_web_search, thread_id,
        )

        try:
            graph = self._get_graph()
            final_state: GraphState = await graph.ainvoke(initial_state, config=config)
        except AppException:
            raise
        except Exception as exc:
            logger.error("[PLAN SVC] Subgraph error for disease=%s: %s", disease_name, exc, exc_info=True)
            raise AppException(ErrorCode.RAG_PIPELINE_ERROR, str(exc))

        # 5. Build and return result
        result = self._build_result(final_state, disease_name)
        return result


def get_plan_generation_service() -> PlanGenerationService:
    return PlanGenerationService()
