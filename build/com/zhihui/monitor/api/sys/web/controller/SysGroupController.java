package com.zhihui.monitor.api.sys.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import lombok.extern.slf4j.Slf4j;
import com.github.pagehelper.PageInfo;
import com.zhihui.monitor.api.base.controller.AbstractBaseController;
import com.zhihui.monitor.api.sys.service.SysGroupService;
import com.zhihui.monitor.api.sys.vo.SysGroupQueryVo;
import com.zhihui.monitor.api.sys.vo.SysGroupDetailVo;
import com.zhihui.monitor.api.sys.vo.SysGroupCreateVo;
import com.zhihui.monitor.api.sys.vo.SysGroupListVo;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@Slf4j
@Api(value = "业务分组", description = "业务分组")
@RequestMapping(value="/sysGroup")
public class SysGroupController extends AbstractBaseController {

	@Autowired
	private SysGroupService sysGroupService;
	
	@ApiOperation(value = "创建业务分组", notes = "根据SysGroup创建业务分组")
	@RequestMapping(value="add",method=RequestMethod.POST)
	@ResponseBody
	public Result<Void> add(@ModelAttribute("m") SysGroupCreateVo m){
	    try {
			sysGroupService.create(m);
		} catch (Exception e) {
			log.error("系统出现异常:" + e.getMessage(), e);
			return new Result<Void>(
					ResultEnum.EXCEPTION.getCode(), "系统出现异常:" + e.getMessage());
		}
		return Result.successResult();
	}
	
	@ApiOperation(value = "获取业务分组详细信息", notes = "根据url的id来获取业务分组详细信息")
	@ApiImplicitParam(name = "id", value = "业务分组ID", required = true, paramType = "query")
	@RequestMapping(value="get/{id}",method=RequestMethod.GET)
	@ResponseBody
	public Result<SysGroupDetailVo> getById(@PathVariable("id") Long id){
		Result<SysGroupEntity> m = sysGroupService.getById(id);
		return m;
	}
	
	@ApiOperation(value = "更新业务分组详细信息", notes = "根据url的id来指定更新对象，并根据传过来的业务分组信息来更新业务分组详细信息")
	@ApiImplicitParams({ @ApiImplicitParam(name = "m", value = "业务分组详细实体", required = true, dataType = "SysGroupEntity") })
	@RequestMapping(value="update",method=RequestMethod.POST)
	@ResponseBody
	public Result<Void> update(@ModelAttribute("m") SysGroupCreateVo m){
	    try {
			sysGroupService.update(m);
		} catch (Exception e) {
			log.error("系统出现异常:" + e.getMessage(), e);
			return new Result<Void>(ResultEnum.EXCEPTION.getCode(), "系统出现异常:"
					+ e.getMessage());
		}
		return Result.successResult();
	}
	
	@ApiOperation(value = "删除业务分组", notes = "根据url的id来指定删除业务分组对象")
	@ApiImplicitParam(name = "id", value = "业务分组ID", required = true, paramType = "query")
	@RequestMapping(value="delete/{id}",method=RequestMethod.POST)
	@ResponseBody
	public Result<Void> delete(@PathVariable("id") Long id){
	    try {
			sysGroupService.delete(id);
		} catch (Exception e) {
			log.error("系统出现异常:" + e.getMessage(), e);
			return new Result<Void>(ResultEnum.EXCEPTION.getCode(), "系统出现异常:"
					+ e.getMessage());
		}
		return Result.successResult();
	}
	
	@ApiOperation(value = "获取业务分组列表", notes = "获取业务分组列表", response = SysGroupListVo.class)
	@ApiImplicitParams({ @ApiImplicitParam(name = "m", value = "业务分组查询条件", required = true, dataType = "SysGroupWebModel") })
	@RequestMapping(value="list",method=RequestMethod.GET)
	@ResponseBody
	public Result<PageInfo<SysGroupListVo>> list(@ModelAttribute("qv") SysGroupQueryVo qv,
	    @RequestParam(value = "page", required = false, defaultValue = "1") int pageNum,
        @RequestParam(value = "rows", required = false, defaultValue = "12") int pageSize){
        SysGroupEntity qm = new SysGroupEntity();
		PageInfo<SysGroupEntity> listPage = sysGroupService.list(qm, pageNum, pageSize);

        return Result.successResult(PageConverterHelper.covert(listPage, listPage.getList()
                .stream()
                .map(SysGroupListVo::fromSysGroupEntity)
                .collect(Collectors.toList())));
	}
}
