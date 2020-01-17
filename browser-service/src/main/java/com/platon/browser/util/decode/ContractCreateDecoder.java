package com.platon.browser.util.decode;

import com.platon.browser.param.ContractCreateParam;
import com.platon.browser.param.TxParam;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.rlp.RlpList;

import java.util.List;

public class ContractCreateDecoder {
    private ContractCreateDecoder(){}

    static TxParam decode(RlpList rootList, List<Log> logs) {

        return ContractCreateParam.builder().build();
    }
}