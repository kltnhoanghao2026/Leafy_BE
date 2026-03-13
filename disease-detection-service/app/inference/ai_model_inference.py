"""
Inference layer for abstracting ML model operations
"""
import logging
from pathlib import Path
import numpy as np

import tensorflow as tf
from ultralytics import YOLO

from app.config.config import config
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode

logger = logging.getLogger(__name__)

class_names = None

def load_class_names(model) -> list[str]:
    """Load class names for the coffee classification model"""
    global class_names
    if class_names is not None:
        return class_names
        
    try:
        num_classes = model.output_shape[-1]
        coffee_classes = ["healthy", "miner", "phoma", "red_spider_mite", "rust"]
        
        if num_classes == len(coffee_classes):
             class_names = coffee_classes
        elif num_classes < len(coffee_classes):
             class_names = coffee_classes[:num_classes]
        else:
             class_names = [f"coffee_class_{i}" for i in range(num_classes)]
             
        logger.info(f"Loaded {len(class_names)} class names for coffee model")
        return class_names
    except Exception as e:
        logger.error(f"Failed to load class names: {e}")
        return [f"class_{i}" for i in range(10)]

class AIModelInference:
    """Abstracts inference operations to keep services decoupled from models"""
    
    @staticmethod
    def load_ml_model():
        try:
             logger.info(f"Loading {config.MODEL_NAME} model from {config.MODEL_PATH}...")
             model = tf.keras.models.load_model(config.MODEL_PATH)
             logger.info(f"Model loaded successfully from {config.MODEL_PATH}")
             load_class_names(model)
             return model
        except FileNotFoundError:
             logger.error(f"Model file not found at {config.MODEL_PATH}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Model file not found: {config.MODEL_PATH}")
        except Exception as e:
             logger.error(f"Failed to load model: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Failed to load model: {e}")
             
    @staticmethod
    def warmup_ml_model(model):
        try:
             logger.info("Warming up model with dummy inference...")
             dummy_input = np.random.rand(1, *config.MODEL_INPUT_SIZE, 3).astype(np.float32)
             _ = model.predict(dummy_input, verbose=0)
             logger.info("Model warmup completed")
        except Exception as e:
             logger.error(f"Model warmup failed: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Model warmup failed: {e}")

    @staticmethod
    def perform_inference(model, image_array: np.ndarray) -> list[dict]:
        try:
            predictions = model.predict(image_array, verbose=0)
            top_indices = np.argsort(predictions[0])[-config.MODEL_TOP_K:][::-1]
            
            results = []
            for idx in top_indices:
                if idx < len(class_names):
                    class_name = class_names[idx]
                else:
                    class_name = f"unknown_class_{idx}"
                    
                confidence = float(predictions[0][idx])
                results.append({
                    "className": class_name,
                    "confidenceScore": confidence
                })
            return results
        except Exception as e:
            logger.error(f"Inference failed: {e}")
            raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Inference failed: {e}")

    @staticmethod
    def load_tflite_model():
        try:
            logger.info(f"Loading TFLite model from {config.TFLITE_MODEL_PATH}...")
            interpreter = tf.lite.Interpreter(model_path=config.TFLITE_MODEL_PATH)
            interpreter.allocate_tensors()
            logger.info(f"TFLite model loaded successfully")
            return interpreter
        except FileNotFoundError:
            logger.error(f"TFLite model file not found at {config.TFLITE_MODEL_PATH}")
            raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"TFLite model file not found: {config.TFLITE_MODEL_PATH}")
        except Exception as e:
             logger.error(f"Failed to load TFLite model: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Failed to load TFLite model: {e}")

    @staticmethod
    def warmup_tflite_model(interpreter):
        try:
             logger.info("Warming up TFLite model with dummy inference...")
             input_details = interpreter.get_input_details()
             output_details = interpreter.get_output_details()
             input_shape = input_details[0]['shape']
             dummy_input = np.random.rand(*input_shape).astype(np.float32)
             
             interpreter.set_tensor(input_details[0]['index'], dummy_input)
             interpreter.invoke()
             _ = interpreter.get_tensor(output_details[0]['index'])
             logger.info("TFLite model warmup completed")
        except Exception as e:
             logger.error(f"TFLite model warmup failed: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"TFLite model warmup failed: {e}")

    @staticmethod
    def perform_tflite_inference(interpreter, image_array: np.ndarray, num_classes: int = 5) -> list[dict]:
         try:
             input_details = interpreter.get_input_details()
             output_details = interpreter.get_output_details()
             interpreter.set_tensor(input_details[0]['index'], image_array)
             interpreter.invoke()
             predictions = interpreter.get_tensor(output_details[0]['index'])
             top_indices = np.argsort(predictions[0])[-config.MODEL_TOP_K:][::-1]
             
             global class_names
             if class_names is None:
                 coffee_classes = ["healthy", "miner", "phoma", "red_spider_mite", "rust"]
                 class_names = coffee_classes[:num_classes]
                 
             results = []
             for idx in top_indices:
                 if idx < len(class_names):
                     class_name = class_names[idx]
                 else:
                     class_name = f"unknown_class_{idx}"
                 confidence = float(predictions[0][idx])
                 results.append({
                     "className": class_name,
                     "confidenceScore": confidence
                 })
             return results
         except Exception as e:
              logger.error(f"TFLite inference failed: {e}")
              raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"TFLite inference failed: {e}")

    @staticmethod
    def load_yolo_model(model_path: str) -> YOLO:
        try:
             logger.info(f"Loading YOLO model from {model_path}...")
             # Only strictly verify existence if the path implies a local directory structure
             if ("/" in model_path or "\\" in model_path) and not Path(model_path).exists():
                  raise FileNotFoundError(f"Model file not found: {model_path}")
             model = YOLO(model_path)
             logger.info(f"YOLO model loaded successfully from {model_path}")
             return model
        except FileNotFoundError:
             logger.error(f"Model file not found at {model_path}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"YOLO model file not found: {model_path}")
        except Exception as e:
             logger.error(f"Failed to load YOLO model: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Failed to load YOLO model: {e}")

    @staticmethod
    def warmup_yolo_model(model: YOLO):
         try:
             logger.info("Warming up YOLO model with dummy inference...")
             dummy_image = np.random.randint(0, 255, (640, 640, 3), dtype=np.uint8)
             _ = model.predict(dummy_image, verbose=False)
             logger.info("YOLO model warmup completed")
         except Exception as e:
             logger.error(f"YOLO model warmup failed: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"YOLO model warmup failed: {e}")

    @staticmethod
    def perform_yolo_inference(model: YOLO, image, confidence_threshold: float = 0.25):
         try:
             results = model.predict(source=image, conf=confidence_threshold, verbose=False)
             return results
         except Exception as e:
             logger.error(f"YOLO inference failed: {e}")
             raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"YOLO Inference error: {str(e)}")
