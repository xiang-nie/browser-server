package com.platon.browser.dao.mapper;

import com.platon.browser.dao.entity.Address;
import com.platon.browser.dao.entity.AddressExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

public interface AddressMapper {
    long countByExample(AddressExample example);

    int deleteByExample(AddressExample example);

    int deleteByPrimaryKey(String address);

    int insert(Address record);

    int insertSelective(Address record);

    List<Address> selectByExampleWithBLOBs(AddressExample example);

    List<Address> selectByExample(AddressExample example);

    Address selectByPrimaryKey(String address);

    int updateByExampleSelective(@Param("record") Address record, @Param("example") AddressExample example);

    int updateByExampleWithBLOBs(@Param("record") Address record, @Param("example") AddressExample example);

    int updateByExample(@Param("record") Address record, @Param("example") AddressExample example);

    int updateByPrimaryKeySelective(Address record);

    int updateByPrimaryKeyWithBLOBs(Address record);

    int updateByPrimaryKey(Address record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table address
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsertNewly(List<Address> list);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table address
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsertSelective(@Param("list") List<Address> list, @Param("selective") Address.Column ... selective);

    /**
     * 统计账户地址的质押金额信息、委托金额、委托节点数量等信息
     */
    void updateStakingDelegationStats();

    Set<String> filterExists(Set<String> pendingAddressSet);

    void batchUpdateSuicided(List<Address> suicidedAddrList);

    void batchUpdateReward(List<Address> rewardClaimAddrList);

    void batchResetType(List<Address> resetTypeAddrList);
}
