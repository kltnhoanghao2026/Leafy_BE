from app.exceptions.error_code import ErrorCode

class AppException(Exception):
    def __init__(self, error_code: ErrorCode, detail: str = None):
        super().__init__()
        self.error_code = error_code
        self.detail = detail if detail else error_code.message_key
        self.code = error_code.code
        self.status_code = error_code.status_code
