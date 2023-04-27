package com.platon.browser.cache;

import com.platon.browser.bean.CustomAddress;
import com.platon.browser.dao.entity.Address;
import com.platon.browser.dao.entity.Token;
import com.platon.browser.dao.mapper.AddressMapper;
import com.platon.browser.dao.mapper.TokenMapper;
import com.platon.browser.enums.AddressTypeEnum;
import com.platon.browser.enums.ContractDescEnum;
import com.platon.browser.enums.ContractTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NewAddressCache {

    @Resource
    private AddressMapper addressMapper;

    @Resource
    private TokenMapper tokenMapper;

    /**
     * 缓存范围：进程
     */
    //private Map<String, ContractTypeEnum> ContractTypeCache = new HashMap<>();
    /**
     * 缓存范围：进程
     */
    private Map<String, AddressTypeEnum> AllAddressTypeCache = new HashMap<>();

    /**
     * 缓存范围：进程
     */
    //private Map<String, ContractTypeEnum> ContractTypeCache = new HashMap<>();
    /**
     * 缓存范围：进程
     */
    private Map<String, Token> AllTokenCache = new HashMap<>();

    //
    /**
     * 缓存范围：区块
     * 2023/04/12 lvxiaoyi 区块中所有交易涉及的地址集合，区块中交易处理完毕后，需要根据此map的数据，新增或者更新address表。
     * 实际上，我们并没有把区块涉及的所有地址都加入此缓存，只是把新发现的的、销毁的，或者有需要更新的地址才加入此缓存
     */
    private Map<String, CustomAddress> blockRelatedAddressCache = new HashMap<>();

    public void clearBlockRelatedAddressCache(){
        blockRelatedAddressCache.clear();
    }
    public void addNewContractAddressToBlockCtx(CustomAddress address){
        AllAddressTypeCache.put(address.getAddress(), AddressTypeEnum.getEnum(address.getType()));
        blockRelatedAddressCache.put(address.getAddress(), address);
    }
    public void addSuicidedAddressToBlockCtx(String address){
        CustomAddress relatedAddress = CustomAddress.createNewAccountAddress(address);
        relatedAddress.setOption(CustomAddress.Option.SUICIDED);
        //把销毁的合约地址在当前block的上下文中
        //todo:考虑下：销毁的合约类型是否要加入缓存
        blockRelatedAddressCache.put(address, relatedAddress);
    }

    public void addPendingAddressToBlockCtx(String address){
        AddressTypeEnum addressTypeEnum = this.getAddressType(address);
        if (addressTypeEnum == null) { //地址在db中没有，是新地址
            this.addAddressTypeCache(address, AddressTypeEnum.ACCOUNT);
            CustomAddress customAddress = CustomAddress.createNewAccountAddress(address);
            customAddress.setOption(CustomAddress.Option.NEW);
            blockRelatedAddressCache.put(address, customAddress);
        }
    }

    public void addRewardClaimAddressToBlockCtx(String address, BigDecimal delegateReward){
        AddressTypeEnum addressTypeEnum = this.getAddressType(address);
        if (addressTypeEnum == null) { //地址在db中没有，是新地址
            this.addAddressTypeCache(address, AddressTypeEnum.ACCOUNT);
            CustomAddress customAddress = CustomAddress.createNewAccountAddress(address);
            customAddress.setHaveReward(delegateReward);
            customAddress.setOption(CustomAddress.Option.NEW);
            blockRelatedAddressCache.put(address, customAddress);
        }

        CustomAddress customAddress = blockRelatedAddressCache.get(address);
        if (customAddress == null) {
            customAddress = CustomAddress.createPendingAccountAddress(address);
            blockRelatedAddressCache.put(address, customAddress);
        }
        customAddress.setHaveReward(delegateReward);
        customAddress.setOption(CustomAddress.Option.REWARD_CLAIM);
    }

    public void addAddressTypeCache(String address, AddressTypeEnum addressTypeEnum){
        AllAddressTypeCache.put(address, addressTypeEnum);
    }

    /**
     * 是否是token，token是合约的子集，需满足erc20,721,1155协议
     * @param address
     * @return
     */
    public boolean isToken(String address){
        AddressTypeEnum addressTypeEnum = this.getAddressType(address);
        if (addressTypeEnum != null && (addressTypeEnum == AddressTypeEnum.ERC20_EVM_CONTRACT || addressTypeEnum == AddressTypeEnum.ERC721_EVM_CONTRACT || addressTypeEnum == AddressTypeEnum.ERC1155_EVM_CONTRACT)){
            return true;
        }
        return false;

    }

    /**
     * 有地址类型，说明地址在address表中已经存在
     * @param address
     * @return
     */
    public AddressTypeEnum getAddressType(String address){
        if(AllAddressTypeCache.containsKey(address)){ //内置地址已经初始到AddressTypeCache
            return AllAddressTypeCache.get(address);
        }else{
            //持续加载到缓存中
            Address addressEntity = addressMapper.selectByPrimaryKey(address);
            if(addressEntity==null){
                return null;
            }else {
                AddressTypeEnum addressTypeEnum = AddressTypeEnum.getEnum(addressEntity.getType());
                AllAddressTypeCache.put(address, addressTypeEnum);
                return addressTypeEnum;
            }
        }
    }


    public ContractTypeEnum getContractType(String address){
        return this.convertAddressType2ContractType(this.getAddressType(address));
    }

    public void init() {
        log.debug("初始化内置地址的合约类型到缓存");
        for (ContractDescEnum contractDescEnum : ContractDescEnum.values()) {
            this.AllAddressTypeCache.put(contractDescEnum.getAddress(), AddressTypeEnum.INNER_CONTRACT);
            //this.ContractTypeCache.put(contractDescEnum.getAddress(), ContractTypeEnum.INNER);
        }
        log.debug("预加载所有token到缓存");
        List<Token> tokens = tokenMapper.selectByExample(null);
        tokens.forEach(token -> {
            this.addTokenCache(token.getAddress(), token);
        });
    }

    public List<Address> listNewAddressInBlockCtx(){
        return blockRelatedAddressCache.values().stream().filter(customAddress -> customAddress.hasOption(CustomAddress.Option.NEW)).collect(Collectors.toList());
    }

    public boolean hasRelatedAddressInBlockCtx(){
        return blockRelatedAddressCache.size()>0;
    }

    public List<Address> listRewardClaimAddressInBlockCtx(){
        return blockRelatedAddressCache.values().stream().filter(customAddress -> customAddress.hasOption(CustomAddress.Option.REWARD_CLAIM)).collect(Collectors.toList());
    }

    public List<Address> listSuicidedAddressInBlockCtx(){
        return blockRelatedAddressCache.values().stream().filter(customAddress -> customAddress.hasOption(CustomAddress.Option.SUICIDED)).collect(Collectors.toList());
    }



    /*public List<Address> listPendingCustomAddressInBlockCtx(){
        return blockRelatedAddressCache.values().stream().filter(customAddress -> customAddress.getOption().equals(CustomAddress.Option.PENDING)).collect(Collectors.toList());
    }

    public Set<String> listPendingAddressInBlockCtx(){
        return blockRelatedAddressCache.values().stream()
                .filter(customAddress -> customAddress.getOption().equals(CustomAddress.Option.PENDING))
                .map(Address::getAddress)
                .collect(Collectors.toSet());
    }

    public void identifyNewAddressInBlockCtx(Set<String> newlyAddressSet) {
        newlyAddressSet.forEach(newAddress -> blockRelatedAddressCache.get(newAddress).setOption(CustomAddress.Option.NEW));
    }*/




    public static AddressTypeEnum convertContractType2AddressType(ContractTypeEnum contractTypeEnum){
        if(contractTypeEnum==null || contractTypeEnum==ContractTypeEnum.UNKNOWN) {
            return AddressTypeEnum.ACCOUNT;
        }
        switch (contractTypeEnum) {
            case WASM:
                return AddressTypeEnum.WASM_CONTRACT;
            case ERC20_EVM:
                return AddressTypeEnum.ERC20_EVM_CONTRACT;
            case ERC721_EVM:
                return AddressTypeEnum.ERC721_EVM_CONTRACT;
            case ERC1155_EVM:
                return AddressTypeEnum.ERC1155_EVM_CONTRACT;
            default:
                return AddressTypeEnum.EVM_CONTRACT;
        }
    }

    private ContractTypeEnum convertAddressType2ContractType(AddressTypeEnum addressTypeEnum){
        if(addressTypeEnum==null) {
            return null;
        }
        switch (addressTypeEnum) {
            case EVM_CONTRACT:
                return ContractTypeEnum.EVM;
            case WASM_CONTRACT:
                return ContractTypeEnum.WASM;
            case ERC20_EVM_CONTRACT:
                return ContractTypeEnum.ERC20_EVM;
            case ERC721_EVM_CONTRACT:
                return ContractTypeEnum.ERC721_EVM;
            case ERC1155_EVM_CONTRACT:
                return ContractTypeEnum.ERC1155_EVM;
            default:
                return null;
        }
    }


    public boolean isEvmContractAddress(String address) {
        return AddressTypeEnum.EVM_CONTRACT == this.getAddressType(address);
    }
    public boolean isWasmContractAddress(String address) {
        return AddressTypeEnum.WASM_CONTRACT == this.getAddressType(address);
    }
    public boolean isErc20ContractAddress(String address) {
        return AddressTypeEnum.ERC20_EVM_CONTRACT == this.getAddressType(address);
    }

    public boolean isErc721ContractAddress(String address) {
        return AddressTypeEnum.ERC721_EVM_CONTRACT == this.getAddressType(address);
    }

    public boolean isErc1155ContractAddress(String address) {
        return AddressTypeEnum.ERC1155_EVM_CONTRACT == this.getAddressType(address);
    }


    public void addTokenCache(String address, Token token){
        AllTokenCache.put(address, token);
    }

    public Token getToken(String address) {
        if(AllTokenCache.containsKey(address)){
            return AllTokenCache.get(address);
        }else{
            //持续加载到缓存中
            Token token = tokenMapper.selectByPrimaryKey(address);
            if(token==null){
                return null;
            }else {
                AllTokenCache.put(address, token);
                return token;
            }
        }
    }
}