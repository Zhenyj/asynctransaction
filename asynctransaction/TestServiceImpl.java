package com.zyj.asynctransaction;

import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.zyj.asynctransaction.dao.TestMapper;
import org.springframework.stereotype.Service;

/**
 * @author lulx
 * @date 2022-08-30 19:55
 **/
@Service
public class TestServiceImpl extends ServiceImpl<TestMapper, TestEntity> implements ITestService {
}
