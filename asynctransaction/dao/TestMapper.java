package com.zyj.asynctransaction.dao;

import com.baomidou.mybatisplus.mapper.BaseMapper;
import com.zyj.asynctransaction.TestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author lulx
 * @date 2022-08-30 19:55
 **/
@Mapper
public interface TestMapper extends BaseMapper<TestEntity> {
}
