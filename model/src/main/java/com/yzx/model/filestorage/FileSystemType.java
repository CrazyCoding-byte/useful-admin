package com.yzx.model.filestorage;

import lombok.Getter;

/**
 * Description:
 *
 * @author: aoxiang
 * @create: 2025-03-11 17:19
 **/
@Getter
public enum FileSystemType {
    // WAContactAvatar
    WAMediaMessage("WAMediaMessage"),
    UserAvatar("UserAvatar"),
    WAContactAvatar("WAContactAvatar"),
    MINIO("minio"),
    MessengerMessageAttachment("MessengerMessageAttachment"),
    LOCAL("local");
    private final String type;

    FileSystemType(String type) {
        this.type = type;
    }

    public static boolean isAvailableType(String type) {
        for (FileSystemType fileSystemType : FileSystemType.values()) {
            if (fileSystemType.getType().equals(type)) {
                return true;
            }
        }
        return false;
    }
}
