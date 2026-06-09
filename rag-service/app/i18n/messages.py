from fastapi import Request

DEFAULT_LOCALE = "vi"

MESSAGES = {
    "vi": {
        "error.auth.unauthenticated": "Xac thuc that bai hoac chua duoc cung cap",
        "error.auth.unauthorized": "Khong du quyen de thuc hien thao tac nay",
        "error.sys.uncategorized": "Da xay ra loi he thong khong xac dinh",
        "error.validation.error": "Xac thuc du lieu that bai",
        "error.file.too.large": "Tep vuot qua gioi han kich thuoc 20 MB",
        "error.file.unsupported.mime": "Dinh dang tep khong duoc ho tro",
        "error.file.save.failed": "Khong the luu tep tai len",
        "error.task.not.found": "Khong tim thay tac vu",
        "error.rag.pipeline": "Xu ly RAG pipeline that bai",
        "error.treatment.plan.not.found": "Khong tim thay ke hoach dieu tri",
        "error.treatment.plan.access.denied": "Khong co quyen truy cap ke hoach dieu tri",
        "error.conversation.not.found": "Khong tim thay hoi thoai",
        "error.conversation.access.denied": "Khong co quyen truy cap hoi thoai",
        "response.success": "Thanh cong",
        "response.document.exists": "Tai lieu da ton tai",
        "response.document.accepted": "Tai lieu da duoc tiep nhan de xu ly",
        "response.treatment.plan.deleted": "Xoa ke hoach dieu tri thanh cong",
        "response.conversation.deleted": "Xoa hoi thoai thanh cong",
        "response.chat.no_answer": "Khong the tao cau tra loi",
        "task.created": "Tac vu da duoc tao",
        "task.processing.start": "Bat dau xu ly",
        "task.processing.parse": "Dang trich xuat noi dung tai lieu",
        "task.processing.empty": "Noi dung trich xuat rong",
        "task.processing.clean": "Dang lam sach noi dung",
        "task.processing.split": "Dang tach van ban",
        "task.processing.layout_parse": "Phan tich bo cuc tai lieu",
        "task.processing.section_chunk": "Tach doan theo cau truc phan",
        "task.processing.store_chunks": "Luu tru cac doan vao co so du lieu",
        "task.processing.index": "Dang lap chi muc vector",
        "task.processing.failed": "Xu ly that bai",
        "task.completed": "Lap chi muc tai lieu thanh cong",
    },
    "en": {
        "error.auth.unauthenticated": "Authentication required or not provided",
        "error.auth.unauthorized": "Insufficient permissions",
        "error.sys.uncategorized": "An uncategorized system error occurred",
        "error.validation.error": "Request validation failed",
        "error.file.too.large": "File exceeds the 20 MB size limit",
        "error.file.unsupported.mime": "Unsupported file type",
        "error.file.save.failed": "Failed to save uploaded file",
        "error.task.not.found": "Task not found",
        "error.rag.pipeline": "RAG pipeline execution failed",
        "error.treatment.plan.not.found": "Treatment plan not found",
        "error.treatment.plan.access.denied": "Access to treatment plan denied",
        "error.conversation.not.found": "Conversation not found",
        "error.conversation.access.denied": "Access to conversation denied",
        "response.success": "Success",
        "response.document.exists": "Document already exists.",
        "response.document.accepted": "Document accepted for processing.",
        "response.treatment.plan.deleted": "Treatment plan deleted successfully.",
        "response.conversation.deleted": "Conversation deleted successfully.",
        "response.chat.no_answer": "I could not generate an answer.",
        "task.created": "Task created",
        "task.processing.start": "Starting processing",
        "task.processing.parse": "Parsing document",
        "task.processing.empty": "Extracted content was empty",
        "task.processing.clean": "Cleaning text",
        "task.processing.split": "Splitting text",
        "task.processing.layout_parse": "Analyzing document layout",
        "task.processing.section_chunk": "Section-aware chunking",
        "task.processing.store_chunks": "Storing chunks to database",
        "task.processing.index": "Indexing vectors",
        "task.processing.failed": "Processing failed",
        "task.completed": "Document indexed successfully",
    },
}


def resolve_locale(request: Request) -> str:
    accept_language = request.headers.get("accept-language", "")
    if not accept_language:
        return DEFAULT_LOCALE

    primary = accept_language.split(",", maxsplit=1)[0].split(";", maxsplit=1)[0].strip().lower()
    if not primary:
        return DEFAULT_LOCALE

    base = primary.split("-", maxsplit=1)[0]
    if primary in MESSAGES:
        return primary
    if base in MESSAGES:
        return base
    return DEFAULT_LOCALE


def get_message(key: str, locale: str | None = None, **kwargs) -> str:
    lang = (locale or DEFAULT_LOCALE).lower()
    base = lang.split("-", maxsplit=1)[0]

    template = (
        MESSAGES.get(lang, {}).get(key)
        or MESSAGES.get(base, {}).get(key)
        or MESSAGES[DEFAULT_LOCALE].get(key)
        or MESSAGES["en"].get(key)
        or key
    )

    if kwargs:
        try:
            return template.format(**kwargs)
        except Exception:
            return template
    return template
