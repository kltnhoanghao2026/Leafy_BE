from fastapi import APIRouter

from app.controllers.prediction_controller import router as prediction_router
from app.controllers.leaf_detection_controller import router as leaf_detection_router
from app.controllers.diagnose_controller import router as diagnose_router

router = APIRouter(prefix="/diseases")
router.include_router(prediction_router)
router.include_router(leaf_detection_router)
router.include_router(diagnose_router)
