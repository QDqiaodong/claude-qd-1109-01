package com.bee.apiary.dto;

public class ActionState {

    public boolean enabled;
    public String reason;

    public ActionState() {
    }

    public ActionState(boolean enabled, String reason) {
        this.enabled = enabled;
        this.reason = reason;
    }
}
