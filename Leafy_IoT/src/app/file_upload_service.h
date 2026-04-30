#pragma once

#include <Arduino.h>

#include "app/camera_service.h"

namespace leafy {

class FileUploadService {
 public:
  bool uploadMultipart(const String& endpoint, const CameraService::Frame& frame, String& fileId, String& error);
};

}  // namespace leafy
