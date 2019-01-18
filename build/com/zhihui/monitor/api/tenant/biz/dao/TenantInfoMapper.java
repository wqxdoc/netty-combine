package com.zhihui.monitor.api.tenant.dao;

import com.zhihui.monitor.api.tenant.entity.TenantInfoEntity;
import com.zhihui.monitor.api.base.common.dao.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface TenantInfoMapper extends BaseMapper<TenantInfoEntity> {
	
}
