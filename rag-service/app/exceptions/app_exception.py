from app.exceptions.error_code import ErrorCode


class AppException(Exception):
    """
    Domain exception carrying an ErrorCode.
    Mirrors auth-service / disease-detection-service AppException.
    """

    def __init__(self, error_code: ErrorCode, detail: str = None):
        self.error_code = error_code
        self.detail = detail
        super().__init__(self.detail)
