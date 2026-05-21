import io
import time
import zipfile
import logging
from fastapi import UploadFile, Response

from app.inference.ai_model_inference import AIModelInference
from app.dto.leaf_detection.leaf_detection_dto import LeafDetectionResponse, Detection, BoundingBox
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from PIL import Image, ImageDraw, ImageFont

logger = logging.getLogger(__name__)

class LeafDetectionService:

    @staticmethod
    def validate_file(file: UploadFile) -> bytes:
        if not file.content_type or not file.content_type.startswith('image/'):
            raise AppException(ErrorCode.INVALID_FILE_TYPE)
        
        image_bytes = file.file.read()
        if len(image_bytes) == 0:
            raise AppException(ErrorCode.EMPTY_FILE)
        return image_bytes

    @staticmethod
    def preprocess_image(image_bytes: bytes):
         try:
             image = Image.open(io.BytesIO(image_bytes))
             if image.mode != 'RGB':
                 image = image.convert('RGB')
             original_width, original_height = image.size
             return image, original_width, original_height
         except Exception as e:
             logger.error(f"Image preprocessing failed: {e}")
             raise AppException(ErrorCode.BAD_REQUEST, f"Invalid image format: {str(e)}")

    @classmethod
    def process_inference(cls, model, image, confidence_threshold: float, class_names: list = None):
        try:
            results = AIModelInference.perform_yolo_inference(model, image, confidence_threshold, class_names)
            detections = []
            if len(results) > 0:
                result = results[0]
                if result.boxes is not None and len(result.boxes) > 0:
                    boxes = result.boxes.xyxy.cpu().numpy() if hasattr(result.boxes.xyxy, 'cpu') else result.boxes.xyxy
                    confidences = result.boxes.conf.cpu().numpy() if hasattr(result.boxes.conf, 'cpu') else result.boxes.conf
                    class_ids = result.boxes.cls.cpu().numpy().astype(int) if hasattr(result.boxes.cls, 'cpu') else result.boxes.cls.astype(int)
                    class_names_map = result.names

                    for box, conf, class_id in zip(boxes, confidences, class_ids):
                        detection = Detection(
                            className=class_names_map[class_id],
                            confidenceScore=float(conf),
                            boundingBox=BoundingBox(
                                x1=float(box[0]), y1=float(box[1]),
                                x2=float(box[2]), y2=float(box[3])
                            )
                        )
                        detections.append(detection)
            return detections
        except Exception as e:
            logger.error(f"YOLO inference failed: {e}")
            raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Inference error: {str(e)}")

    @classmethod
    def detect_leaf(cls, file: UploadFile, model, class_names: list, model_name: str, confidence: float) -> dict:
        start_time = time.time()
        try:
             image_bytes = cls.validate_file(file)
             image, original_width, original_height = cls.preprocess_image(image_bytes)

             if not model:
                 raise AppException(ErrorCode.MODEL_NOT_LOADED)

             detections = cls.process_inference(model, image, confidence, class_names)
             processing_time = (time.time() - start_time) * 1000

             response = LeafDetectionResponse(
                  detections=detections,
                  modelName=model_name,
                  imageWidth=original_width,
                  imageHeight=original_height,
                  processingTimeMs=round(processing_time, 2),
                  detectionCount=len(detections)
             )
             return response.model_dump()
        finally:
             file.file.close()

    @classmethod
    def draw_bounding_boxes(cls, image: Image.Image, detections: list[Detection], box_color: tuple, box_thickness: int) -> bytes:
         try:
             img_with_boxes = image.copy()
             draw = ImageDraw.Draw(img_with_boxes)
             try:
                 font = ImageFont.truetype("arial.ttf", 16)
             except:
                 font = ImageFont.load_default()
                 
             for detection in detections:
                 bbox = detection.boundingBox
                 draw.rectangle([(bbox.x1, bbox.y1), (bbox.x2, bbox.y2)], outline=box_color, width=box_thickness)
                 label = f"{detection.className}: {detection.confidenceScore:.2f}"
                 try:
                     text_bbox = draw.textbbox((bbox.x1, bbox.y1 - 20), label, font=font)
                 except:
                     text_bbox = (bbox.x1, bbox.y1 - 20, bbox.x1 + 100, bbox.y1)
                 draw.rectangle(text_bbox, fill=box_color)
                 draw.text((bbox.x1, bbox.y1 - 20), label, fill=(255, 255, 255), font=font)
                 
             img_byte_arr = io.BytesIO()
             img_with_boxes.save(img_byte_arr, format="JPEG")
             return img_byte_arr.getvalue()
         except Exception as e:
              logger.error(f"Failed to draw bounding boxes: {e}")
              raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Error drawing bounding boxes: {str(e)}")

    @classmethod
    def visualize(cls, file: UploadFile, model, class_names: list, confidence: float, box_color: str, box_thickness: int) -> tuple[bytes, int]:
         try:
             image_bytes = cls.validate_file(file)
             image, _, _ = cls.preprocess_image(image_bytes)

             if not model:
                 raise AppException(ErrorCode.MODEL_NOT_LOADED)

             detections = cls.process_inference(model, image, confidence, class_names)

             color_map = {
                 "green": (0, 255, 0), "red": (255, 0, 0), "blue": (0, 0, 255),
                 "yellow": (255, 255, 0), "cyan": (0, 255, 255), "magenta": (255, 0, 255),
                 "white": (255, 255, 255)
             }
             rgb_color = color_map.get(box_color.lower(), (0, 255, 0))

             img_bytes = cls.draw_bounding_boxes(image, detections, rgb_color, box_thickness)
             return img_bytes, len(detections)
         finally:
             file.file.close()

    @classmethod
    def crop_detections(cls, image: Image.Image, detections: list[Detection], padding: int) -> list:
         try:
             cropped_images = []
             img_width, img_height = image.size
             for detection in detections:
                 bbox = detection.boundingBox
                 x1 = max(0, int(bbox.x1) - padding)
                 y1 = max(0, int(bbox.y1) - padding)
                 x2 = min(img_width, int(bbox.x2) + padding)
                 y2 = min(img_height, int(bbox.y2) + padding)
                 cropped = image.crop((x1, y1, x2, y2))
                 cropped_images.append((cropped, detection))
             return cropped_images
         except Exception as e:
              logger.error(f"Failed to crop detections: {e}")
              raise AppException(ErrorCode.INTERNAL_SERVER_ERROR, f"Error cropping detections: {str(e)}")

    @classmethod
    def detect_and_crop(cls, file: UploadFile, model, class_names: list, confidence: float, padding: int, return_format: str) -> tuple[bytes, str, str, int]:
         try:
             image_bytes = cls.validate_file(file)
             image, _, _ = cls.preprocess_image(image_bytes)

             if not model:
                  raise AppException(ErrorCode.MODEL_NOT_LOADED)

             detections = cls.process_inference(model, image, confidence, class_names)
             if len(detections) == 0:
                  raise AppException(ErrorCode.NO_DETECTIONS)

             cropped_images = cls.crop_detections(image, detections, padding)

             if return_format.lower() == "single":
                  cropped_img, detection = cropped_images[0]
                  img_byte_arr = io.BytesIO()
                  cropped_img.save(img_byte_arr, format="JPEG")
                  img_bytes = img_byte_arr.getvalue()
                  filename = f"detection_0_{detection.className}_{detection.confidenceScore:.2f}.jpg"
                  return img_bytes, "image/jpeg", filename, len(detections)
             else:
                  zip_buffer = io.BytesIO()
                  with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zip_file:
                       for idx, (cropped_img, detection) in enumerate(cropped_images):
                            img_byte_arr = io.BytesIO()
                            cropped_img.save(img_byte_arr, format="JPEG")
                            img_bytes = img_byte_arr.getvalue()
                            filename = f"detection_{idx}_{detection.className}_{detection.confidenceScore:.2f}.jpg"
                            zip_file.writestr(filename, img_bytes)
                  return zip_buffer.getvalue(), "application/zip", "leaf_detections.zip", len(detections)
         finally:
              file.file.close()
