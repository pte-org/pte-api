package com.pte.itembank;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rollout switches for the additive task-type contract. Read paths remain
 * available while custom creation and activation are enabled deliberately,
 * after cross-repository verification. Attempt preflight has its own
 * attempt-scoped property because it is enforced at the delivery boundary.
 */
@ConfigurationProperties(prefix = "task-types.rollout")
public class TaskTypeRolloutProperties {

    private boolean customCreationEnabled;
    private boolean customTemplateActivationEnabled;

    public boolean isCustomCreationEnabled() {
        return customCreationEnabled;
    }

    public void setCustomCreationEnabled(boolean customCreationEnabled) {
        this.customCreationEnabled = customCreationEnabled;
    }

    public boolean isCustomTemplateActivationEnabled() {
        return customTemplateActivationEnabled;
    }

    public void setCustomTemplateActivationEnabled(boolean customTemplateActivationEnabled) {
        this.customTemplateActivationEnabled = customTemplateActivationEnabled;
    }

}
