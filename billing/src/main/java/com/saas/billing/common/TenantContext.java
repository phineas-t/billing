package com.saas.billing.common;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORG_ID
            =new ThreadLocal<>();

    private TenantContext(){
        throw new UnsupportedOperationException(
                "TenantContext is a utility class"
        );
    }

    public static void setOrgId(UUID orgId){
        CURRENT_ORG_ID.set(orgId);
    }

    public static UUID getOrgId(){
        return CURRENT_ORG_ID.get();
    }
    //called through finally prevents thread leak or tenant data leak
    public static void clear(){
        CURRENT_ORG_ID.remove();
    }
}
