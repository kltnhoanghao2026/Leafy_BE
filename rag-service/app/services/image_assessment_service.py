import base64
import logging
from typing import Optional

import httpx
from langchain_core.messages import HumanMessage

from app.core.ai_providers import get_gemini_pro

logger = logging.getLogger(__name__)

class ImageAssessmentService:
    """Service to assess disease severity from images using Gemini Vision."""

    def __init__(self):
        # We use gemini-1.5-pro, which inherently supports multimodal inputs.
        self.llm = get_gemini_pro(temperature=0)

    async def assess_image(self, image_url: str, disease_name: str) -> Optional[str]:
        """
        Download the image from the presigned URL and pass it to Gemini
        to assess disease severity.
        """
        if not image_url:
            return None

        try:
            # Download the image
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.get(image_url)
                if response.status_code != 200:
                    logger.warning(f"Failed to fetch image for assessment: {response.status_code}")
                    return None
                
                image_bytes = response.content
                image_b64 = base64.b64encode(image_bytes).decode("utf-8")
                
                # Try to determine mime type, default to jpeg
                content_type = response.headers.get("Content-Type", "image/jpeg")

            prompt = (
                f"You are an expert plant pathologist. Analyze this image of {disease_name} on a coffee plant. "
                "Determine the severity level (LOW, MEDIUM, HIGH, CRITICAL) and provide a 1-sentence explanation of what you see. "
                "Format strictly as 'Severity: [LEVEL] - [Explanation]'"
            )

            message = HumanMessage(
                content=[
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{content_type};base64,{image_b64}"},
                    },
                ]
            )

            result = await self.llm.ainvoke([message])
            assessment = result.content.strip()
            logger.info(f"Image assessment result: {assessment}")
            return assessment

        except Exception as e:
            logger.error(f"Error during image severity assessment: {e}", exc_info=True)
            return None

def get_image_assessment_service() -> ImageAssessmentService:
    return ImageAssessmentService()
