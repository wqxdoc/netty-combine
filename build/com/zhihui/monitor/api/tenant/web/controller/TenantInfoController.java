package com.zhihui.monitor.api.tenant.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import lombok.extern.slf4j.Slf4j;
import com.github.pagehelper.PageInfo;
import com.zhihui.monitor.api.base.controller.AbstractBaseController;
import com.zhihui.monitor.api.tenant.service.TenantInfoService;
import com.zhihui.monitor.api.tenant.vo.TenantInfoQueryVo;
import com.zhihui.monitor.api.tenant.vo.TenantInfoDetailVo;
import com.zhihui.monitor.api.tenant.vo.TenantInfoCreateVo;
import com.zhihui.monitor.api.tenant.vo.TenantInfoListVo;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@Slf4j
@Api(value = "租户", description = "租户")
@RequestMapping(value="/tenantInfo")
public class TenantInfoController extends AbstractBaseController {

	@Autowired
	private TenantInfoService tenantInfoService;
	
	@ApiOperation(value = "创建租户", notes = "根据TenantInfo创建租户")
	@RequestMapping(value="add",method=RequestMethod.POST)
	@ResponseBody
	public Result<Void> add(@ModelAttribute("m") TenantInfoCreateVo m){
	    try {
			tenantInfoService.create(m);
		} catch (Exception e) {
			log.error("系统出现异常:" + e.getMessage(), e);
			return new Result<Void>(
					ResultEnum.EXCEPTION.getCode(), "系统出现异常:" + e.getMessage());
		}
		return Result.successResult();
	}
	
	@ApiOperation(value = "获取租户详细信息", notes = "根据url的id来获取租户详细信息")
	@ApiImplicitParam(name = "id", value = "租户ID", required = true, paramType = "query")
	@RequestMapping(value="get/{id}",method=RequestMethod.GET)
	@ResponseBody
	public Result<TenantInfoDetailVo> getById(@PathVariable("id") Long id){
		Result<TenantInfoEntity> m = tenantInfoService.getById(id);
		return m;
	}
	
	@ApiOperation(value = "更新租户详细信息", notes = "根据url的id来指定更新对象，并根据传过来的租户信息来更新租户详细信息")
	@ApiImplicitParams({ @ApiImplicitParam(name = "m", value = "租户详细实体", required = true, dataType = "TenantInfoEntity") })
	@RequestMapping(value="update",method=RequestMethod.POST)
	@ResponseBody
	public Result<Void> update(@ModelAttribute("m") TenantInfoCreateVo m){
	    try {
			tenantInfoService.update(m);
		} catch (Exception e) {
			log.error("系统出现异常:" + e.getMessage(), e);
			return new Result<Void>(ResultEnum.EXCEPTION.getCode(), "系统出现异常:"
					+ e.getMessage());
		}
		return Result.successResult();
	}
	
	@ApiOperation(value = "删除租户", notes = "根据url的id来指定删除租户对象")
	@ApiImplicitParam(name = "id", value = "租户ID", required = true, paramType = "query")
	@RequestMapping(value="delete/{id}",method=RequestMethod.POST)
	@ResponseBody
	public Result<Void> delete(@PathVariable("id") Long id){
	    try {
			tenantInfoService.delete(id);
		} catch (Exception e) {
			log.error("系统出现异常:" + e.getMessage(), e);
			return new Result<Void>(ResultEnum.EXCEPTION.getCode(), "系统出现异常:"
					+ e.getMessage());
		}
		return Result.successResult();
	}
	
	@ApiOperation(value = "获取租户列表", notes = "获取租户列表", response = TenantInfoListVo.class)
	@ApiImplicitParams({ @ApiImplicitParam(name = "m", value = "租户查询条件", required = true, dataType = "TenantInfoWebModel") })
	@RequestMapping(value="list",method=RequestMethod.GET)
	@ResponseBody
	public Result<PageInfo<TenantInfoListVo>> list(@ModelAttribute("qv") TenantInfoQueryVo qv,
	    @RequestParam(value = "page", required = false, defaultValue = "1") int pageNum,
        @RequestParam(value = "rows", required = false, defaultValue = "12") int pageSize){
        TenantInfoEntity qm = new TenantInfoEntity();
		PageInfo<TenantInfoEntity> listPage = tenantInfoService.list(qm, pageNum, pageSize);

        return Result.successResult(PageConverterHelper.covert(listPage, listPage.getList()
                .stream()
                .map(TenantInfoListVo::fromTenantInfoEntity)
                .collect(Collectors.toList())));
	}
}
