package com.zhihui.monitor.api.sys.service.impl;


import com.zhihui.monitor.api.base.service.AbstractBaseService;
import com.zhihui.monitor.api.sys.entity.SysGroupEntity;
import com.zhihui.monitor.api.sys.service.ISysGroupService;
import com.zhihui.monitor.api.sys.vo.SysGroupQueryModel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service()
@Transactional
public class SysGroupService extends AbstractBaseService<SysGroupMapper, SysGroupEntity> {
	
}