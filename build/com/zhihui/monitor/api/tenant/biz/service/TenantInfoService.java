package com.zhihui.monitor.api.tenant.service.impl;


import com.zhihui.monitor.api.base.service.AbstractBaseService;
import com.zhihui.monitor.api.tenant.entity.TenantInfoEntity;
import com.zhihui.monitor.api.tenant.service.ITenantInfoService;
import com.zhihui.monitor.api.tenant.vo.TenantInfoQueryModel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service()
@Transactional
public class TenantInfoService extends AbstractBaseService<TenantInfoMapper, TenantInfoEntity> {
	
}