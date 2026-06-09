"""
DiagnoseService — business logic for reading and deleting DiagnoseRequest / DiagnoseResult records.
Access rules:
  - ADMIN: can access all records
  - USER: can only access their own records
"""
import logging
from typing import List

from app.config.security import UserPrincipal
from app.dto.diagnose.diagnose_request_dto import DiagnoseRequestResponse
from app.dto.diagnose.diagnose_result_dto import DiagnoseResultResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.repositories.diagnose_repository import DiagnoseRepository

logger = logging.getLogger(__name__)

ROLE_ADMIN = "ROLE_ADMIN"


class DiagnoseService:

    # ------------------------------------------------------------------ #
    #  DiagnoseRequest                                                     #
    # ------------------------------------------------------------------ #

    @staticmethod
    def get_request(diagnose_request_id: str, current_user: UserPrincipal) -> DiagnoseRequestResponse:
        """Return a single DiagnoseRequest. Only the owner or ADMIN may access it."""
        doc = DiagnoseRepository.find_request_by_id(diagnose_request_id)
        if not doc:
            raise AppException(ErrorCode.DIAGNOSE_REQUEST_NOT_FOUND)

        is_admin = ROLE_ADMIN in current_user.roles
        if not is_admin and doc["userId"] != current_user.id:
            raise AppException(ErrorCode.UNAUTHORIZED)

        return DiagnoseRequestResponse.from_doc(doc)

    @staticmethod
    def get_all_requests(
        current_user: UserPrincipal,
        page: int = 0,
        size: int = 20,
    ) -> dict:
        """
        Return a paginated list of DiagnoseRequests.
        ADMIN sees all; USER sees only their own.
        """
        skip = page * size
        is_admin = ROLE_ADMIN in current_user.roles

        if is_admin:
            docs = DiagnoseRepository.find_all_requests(skip=skip, limit=size)
            total = DiagnoseRepository.count_all_requests()
        else:
            docs = DiagnoseRepository.find_requests_by_user(current_user.id, skip=skip, limit=size)
            total = DiagnoseRepository.count_requests_by_user(current_user.id)

        items = [DiagnoseRequestResponse.from_doc(d) for d in docs]
        return {
            "content": [i.model_dump() for i in items],
            "page": page,
            "size": size,
            "totalElements": total,
            "totalPages": (total + size - 1) // size if size else 0,
        }

    @staticmethod
    def delete_request(diagnose_request_id: str, current_user: UserPrincipal) -> None:
        """
        Delete a DiagnoseRequest and its linked DiagnoseResult.
        Only the owner or ADMIN may delete.
        """
        doc = DiagnoseRepository.find_request_by_id(diagnose_request_id)
        if not doc:
            raise AppException(ErrorCode.DIAGNOSE_REQUEST_NOT_FOUND)

        is_admin = ROLE_ADMIN in current_user.roles
        if not is_admin and doc["userId"] != current_user.id:
            raise AppException(ErrorCode.UNAUTHORIZED)

        # Cascade delete result first, then request
        DiagnoseRepository.delete_result_by_request_id(diagnose_request_id)
        DiagnoseRepository.delete_request(diagnose_request_id)
        logger.info(f"Deleted DiagnoseRequest {diagnose_request_id} (and linked result) by user {current_user.id}")

    @staticmethod
    def update_request_plant(diagnose_request_id: str, plant_id: str, current_user: UserPrincipal) -> None:
        """
        Update the plantId of a DiagnoseRequest.
        Only the owner or ADMIN may update it.
        """
        doc = DiagnoseRepository.find_request_by_id(diagnose_request_id)
        if not doc:
            raise AppException(ErrorCode.DIAGNOSE_REQUEST_NOT_FOUND)

        is_admin = ROLE_ADMIN in current_user.roles
        if not is_admin and doc["userId"] != current_user.id:
            raise AppException(ErrorCode.UNAUTHORIZED)

        DiagnoseRepository.update_plant_id(diagnose_request_id, plant_id)
        logger.info(f"Updated plantId={plant_id} for DiagnoseRequest {diagnose_request_id} by user {current_user.id}")

    # ------------------------------------------------------------------ #
    #  DiagnoseResult                                                      #
    # ------------------------------------------------------------------ #

    @staticmethod
    def get_result_by_request(diagnose_request_id: str, current_user: UserPrincipal) -> DiagnoseResultResponse:
        """Return the DiagnoseResult for a given request. Owner or ADMIN only."""
        doc = DiagnoseRepository.find_result_by_request_id(diagnose_request_id)
        if not doc:
            raise AppException(ErrorCode.DIAGNOSE_RESULT_NOT_FOUND)

        is_admin = ROLE_ADMIN in current_user.roles
        if not is_admin and doc["userId"] != current_user.id:
            raise AppException(ErrorCode.UNAUTHORIZED)

        return DiagnoseResultResponse.from_doc(doc)

    @staticmethod
    def get_all_results(
        current_user: UserPrincipal,
        page: int = 0,
        size: int = 20,
    ) -> dict:
        """
        Return a paginated list of DiagnoseResults.
        ADMIN sees all; USER sees only their own.
        """
        skip = page * size
        is_admin = ROLE_ADMIN in current_user.roles

        if is_admin:
            docs = DiagnoseRepository.find_all_results(skip=skip, limit=size)
            total = DiagnoseRepository.count_all_results()
        else:
            docs = DiagnoseRepository.find_results_by_user(current_user.id, skip=skip, limit=size)
            total = DiagnoseRepository.count_results_by_user(current_user.id)

        items = [DiagnoseResultResponse.from_doc(d) for d in docs]
        return {
            "content": [i.model_dump() for i in items],
            "page": page,
            "size": size,
            "totalElements": total,
            "totalPages": (total + size - 1) // size if size else 0,
        }
