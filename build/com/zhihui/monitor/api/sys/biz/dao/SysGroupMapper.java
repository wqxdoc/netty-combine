package com.zhihui.monitor.api.sys.dao;

import com.zhihui.monitor.api.sys.entity.SysGroupEntity;
import com.zhihui.monitor.api.base.common.dao.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface SysGroupMapper extends BaseMapper<SysGroupEntity> {
	
}
