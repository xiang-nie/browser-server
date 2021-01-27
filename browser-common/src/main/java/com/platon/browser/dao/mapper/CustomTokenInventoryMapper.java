package com.platon.browser.dao.mapper;

import com.platon.browser.dao.entity.TokenInventory;
import com.platon.browser.dao.entity.TokenInventoryKey;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomTokenInventoryMapper {

    int batchInsertOrUpdateSelective(@Param("list") List<TokenInventory> list, @Param("selective") TokenInventory.Column... selective);

    void burnAndDelTokenInventory(@Param("list") List<TokenInventoryKey> list);

}