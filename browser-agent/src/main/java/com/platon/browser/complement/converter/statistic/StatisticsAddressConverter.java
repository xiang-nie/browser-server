package com.platon.browser.complement.converter.statistic;

import com.platon.browser.common.collection.dto.CollectionTransaction;
import com.platon.browser.common.collection.dto.EpochMessage;
import com.platon.browser.common.complement.cache.AddressCache;
import com.platon.browser.common.queue.collection.event.CollectionEvent;
import com.platon.browser.complement.dao.mapper.StatisticBusinessMapper;
import com.platon.browser.complement.dao.param.statistic.AddressStatChange;
import com.platon.browser.complement.dao.param.statistic.AddressStatItem;
import com.platon.browser.dao.entity.Address;
import com.platon.browser.dao.entity.AddressExample;
import com.platon.browser.dao.entity.Erc20Token;
import com.platon.browser.dao.entity.Erc20TokenExample;
import com.platon.browser.dao.mapper.AddressMapper;
import com.platon.browser.dao.mapper.Erc20TokenMapper;
import com.platon.browser.dto.CustomAddress;
import com.platon.browser.elasticsearch.dto.Block;
import com.platon.browser.enums.ContractTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class StatisticsAddressConverter {

    @Autowired
    private AddressCache addressCache;
    @Autowired
    private StatisticBusinessMapper statisticBusinessMapper;
    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private Erc20TokenMapper erc20TokenMapper;

    public void convert(CollectionEvent event, Block block, EpochMessage epochMessage) {
        long startTime = System.currentTimeMillis();
        log.debug("block({}),transactions({}),consensus({}),settlement({}),issue({})", block.getNum(),
            event.getTransactions().size(), epochMessage.getConsensusEpochRound(), epochMessage.getSettleEpochRound(),
            epochMessage.getIssueEpochRound());
        // 使用缓存中的地址统计信息构造入库参数列表
        List<AddressStatItem> itemFromCache = new ArrayList<>();
        List<String> addresses = new ArrayList<>();
        this.addressCache.getAll().forEach(cache -> {
            AddressStatItem item = AddressStatItem.builder().address(cache.getAddress()).type(cache.getType())
                .txQty(cache.getTxQty()).transferQty(cache.getTransferQty()).delegateQty(cache.getDelegateQty())
                .stakingQty(cache.getStakingQty()).proposalQty(cache.getProposalQty())
                .contractName(cache.getContractName()).contractCreate(cache.getContractCreate())
                .contractCreatehash(cache.getContractCreatehash()).contractDestroyHash(cache.getContractDestroyHash())
                .contractBin(cache.getContractBin()).haveReward(cache.getHaveReward()).build();
            // 检查当前地址是否是普通合约地址
            ContractTypeEnum contractTypeEnum =
                CollectionTransaction.getGeneralContractAddressCache().get(cache.getAddress());
            if (contractTypeEnum != null) {
                switch (contractTypeEnum) {
                    case WASM:
                        item.setType(CustomAddress.TypeEnum.WASM.getCode());
                        break;
                    case EVM:
                        item.setType(CustomAddress.TypeEnum.EVM.getCode());
                        break;
                    case ERC20_EVM:
                        item.setType(CustomAddress.TypeEnum.ERC20_EVM.getCode());
                        break;
                }
            }
            itemFromCache.add(item);
            addresses.add(cache.getAddress());
        });
        // 清空地址缓存
        this.addressCache.cleanAll();
        // 从数据库中查询出与缓存中对应的地址信息
        AddressExample condition = new AddressExample();
        condition.createCriteria().andAddressIn(addresses);
        List<Address> itemFromDb = this.addressMapper.selectByExampleWithBLOBs(condition);
        // 地址与详情进行映射
        Map<String, Address> dbMap = new HashMap<>();
        itemFromDb.forEach(item -> dbMap.put(item.getAddress(), item));
        // 合并数据库与缓存中的值
        Map<String, AddressStatItem> cacheMap = new HashMap<>();
        itemFromCache.forEach(fromCache -> {
            cacheMap.put(fromCache.getAddress(), fromCache);
            Address fromDb = dbMap.get(fromCache.getAddress());
            if (null != fromDb) {
                fromCache.setTxQty(fromDb.getTxQty() + fromCache.getTxQty()); // 交易数量
                fromCache.setTransferQty(fromDb.getTransferQty() + fromCache.getTransferQty()); // 转账数量
                fromCache.setDelegateQty(fromDb.getDelegateQty() + fromCache.getDelegateQty()); // 委托数量
                fromCache.setStakingQty(fromDb.getStakingQty() + fromCache.getStakingQty()); // 质押数量
                fromCache.setProposalQty(fromDb.getProposalQty() + fromCache.getProposalQty()); // 提案数量
                fromCache.setHaveReward(fromDb.getHaveReward().add(fromCache.getHaveReward())); // 已领取委托奖励总额
                // 合约创建人，数据库的值优先
                String contractCreate = fromDb.getContractCreate();
                if (StringUtils.isBlank(contractCreate))
                    contractCreate = fromCache.getContractCreate();
                fromCache.setContractCreate(contractCreate);
                // 合约创建交易hash，数据库的值优先
                String contractCreateHash = fromDb.getContractCreatehash();
                if (StringUtils.isBlank(contractCreateHash))
                    contractCreateHash = fromCache.getContractCreatehash();
                fromCache.setContractCreatehash(contractCreateHash);
                // 合约销毁交易hash，数据库的值优先
                String contractDestroyHash = fromDb.getContractDestroyHash();
                if (StringUtils.isBlank(contractDestroyHash))
                    contractDestroyHash = fromCache.getContractDestroyHash();
                fromCache.setContractDestroyHash(contractDestroyHash);
                // 合约bin代码数据
                String contractBin = fromDb.getContractBin();
                if (StringUtils.isBlank(contractBin))
                    contractBin = fromCache.getContractBin();
                fromCache.setContractBin(contractBin);
                // 合约名称
                String contractName = fromCache.getContractName();
                if (StringUtils.isBlank(contractName))
                    contractName = fromDb.getContractName();
                fromCache.setContractName(contractName);

                fromCache.setType(fromDb.getType()); // 不能让缓存覆盖数据库中的地址类型
            }
        });

        // 查看交易列表中是否有bin属性为0x的交易,有则对to对应的合约地址进行设置
        event.getTransactions().forEach(tx -> {
            // 如果tx的bin为0x，表明这笔交易是销毁合约交易或调用已销毁合约交易, to地址必定是合约地址
            if ("0x".equals(tx.getBin())) {
                AddressStatItem item = cacheMap.get(tx.getTo());
                if (item != null && StringUtils.isBlank(item.getContractDestroyHash())) {
                    // 如果当前地址缓存的销毁交易地址为空，则设置
                    item.setContractDestroyHash(tx.getHash());
                }
            }

        });

        // 使用合并后的信息构造地址入库参数
        AddressStatChange addressStatChange = AddressStatChange.builder().addressStatItemList(itemFromCache).build();
        this.statisticBusinessMapper.addressChange(addressStatChange);

        log.debug("处理耗时:{} ms", System.currentTimeMillis() - startTime);
    }

    public void erc20TokenConvert(CollectionEvent event, Block block, EpochMessage epochMessage) {
        long startTime = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("erc20TokenConvert ~ block({}), transactions({}), consensus({}), settlement({}), issue({})",
                    block.getNum(), event.getTransactions().size(), epochMessage.getConsensusEpochRound(),
                    epochMessage.getSettleEpochRound(), epochMessage.getIssueEpochRound());
        }
        Collection<Erc20Token> erc20TokenList = addressCache.getAllErc20Token();
        if(erc20TokenList.isEmpty()) {
            return;
        }

        Map<String, Erc20Token> erc20TokenMap = new HashMap<>();
        List<String> addresses = new ArrayList<>();
        erc20TokenList.forEach(cacheErc20Token -> {
            erc20TokenMap.put(cacheErc20Token.getAddress(), cacheErc20Token);
            addresses.add(cacheErc20Token.getAddress());
        });
        this.addressCache.cleanErc20TokenCache();

        // 排除地址重复的数据
        Erc20TokenExample tokenCondition = new Erc20TokenExample();
        tokenCondition.createCriteria().andAddressIn(addresses);
        List<Erc20Token> tokenList = erc20TokenMapper.selectByExample(tokenCondition);

        // 过滤重复的数据，DB 中已经存在的，则不进行再次插入
        tokenList.forEach(dbToken -> {
            erc20TokenMap.remove(dbToken.getAddress());
        });

        // 将增量新增的代币合约录入DB
        List<Erc20Token> params = new ArrayList<>();
        erc20TokenMap.values().forEach(t -> {
            params.add(t);
        });

        // batch save data.
        int result = 0;
        if(null != params && params.size() != 0){
            result = erc20TokenMapper.batchInsert(params);
        }
        if (log.isDebugEnabled()) {
            log.debug("erc20TokenConvert ~ 处理耗时:{} ms", System.currentTimeMillis() - startTime
                    + " 参数条数：{" + params.size() + "}，成功数量：{" + result + "}");
        }

    }
}
