package com.sms.protocol.smgp;


import com.sms.protocol.smgp.message.*;

public class SmgpMessageFactory {

    public static SmgpMessage create(int commandId) {
        switch (commandId) {
            case SmgpConstants.SMGP_LOGIN:
                return new SmgpLogin();
            case SmgpConstants.SMGP_LOGIN_RESP:
                return new SmgpLoginResp();
            case SmgpConstants.SMGP_SUBMIT:
                return new SmgpSubmit();
            case SmgpConstants.SMGP_SUBMIT_RESP:
                return new SmgpSubmitResp();
            case SmgpConstants.SMGP_DELIVER:
                return new SmgpDeliver();
            case SmgpConstants.SMGP_DELIVER_RESP:
                return new SmgpDeliverResp();
            case SmgpConstants.SMGP_ACTIVE_TEST:
                return new SmgpActiveTest();
            case SmgpConstants.SMGP_ACTIVE_TEST_RESP:
                return new SmgpActiveTestResp();
            case SmgpConstants.SMGP_FORWARD:
                return new SmgpForward();
            case SmgpConstants.SMGP_FORWARD_RESP:
                return new SmgpForwardResp();
            case SmgpConstants.SMGP_EXIT:
                return new SmgpExit();
            case SmgpConstants.SMGP_EXIT_RESP:
                return new SmgpExitResp();
            case SmgpConstants.SMGP_QUERY:
                return new SmgpQuery();
            case SmgpConstants.SMGP_QUERY_RESP:
                return new SmgpQueryResp();
            default:
                throw new IllegalArgumentException("Unknown commandId: " + commandId);
        }
    }


}
