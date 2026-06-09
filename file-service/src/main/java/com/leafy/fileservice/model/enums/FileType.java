package com.leafy.fileservice.model.enums;

/**
 * Broad category of an uploaded file, derived from its MIME type.
 */
public enum FileType {
    PDF,
    IMAGE,
    DOCUMENT,
    OTHER;

    public static FileType fromContentType(String contentType) {
        if (contentType == null) return OTHER;
        String ct = contentType.toLowerCase();
        if (ct.equals("application/pdf")) return PDF;
        if (ct.startsWith("image/")) return IMAGE;
        if (ct.equals("application/msword")
                || ct.startsWith("application/vnd.openxmlformats-officedocument")
                || ct.startsWith("application/vnd.oasis.opendocument")) {
            return DOCUMENT;
        }
        return OTHER;
    }
}
