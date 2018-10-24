package com.platon.browser.dao.mapper;

import com.platon.browser.dao.entity.Node;
import com.platon.browser.dao.entity.NodeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NodeMapper {
    long countByExample(NodeExample example);

    int deleteByExample(NodeExample example);

    int deleteByPrimaryKey(String nodeId);

    int insert(Node record);

    int insertSelective(Node record);

    List<Node> selectByExample(NodeExample example);

    Node selectByPrimaryKey(String nodeId);

    int updateByExampleSelective(@Param("record") Node record, @Param("example") NodeExample example);

    int updateByExample(@Param("record") Node record, @Param("example") NodeExample example);

    int updateByPrimaryKeySelective(Node record);

    int updateByPrimaryKey(Node record);
}