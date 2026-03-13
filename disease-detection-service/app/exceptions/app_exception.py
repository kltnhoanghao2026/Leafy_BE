from app.exceptions.error_code import ErrorCode

class AppException(Exception):
    def __init__(self, error_code: ErrorCode, message: str = None):
        super().__init__()
        self.error_code = error_code
        # Allow overriding the default message
        self.message = message if message else error_code.message
        self.code = error_code.code
        self.status_code = error_code.status_code
