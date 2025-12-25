package com.docst.domain;

public enum ProjectRole {
    OWNER,
    ADMIN,
    EDITOR,
    VIEWER;

    public boolean hasPermission(ProjectRole required) {
        return this.ordinal() <= required.ordinal();
    }
}
