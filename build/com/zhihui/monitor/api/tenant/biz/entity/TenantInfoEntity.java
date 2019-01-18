package com.zhihui.monitor.api.tenant.entity;

import com.heda.idss.base.vo.BaseModel;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TenantInfoEntity extends BaseModel {

	private static final long serialVersionUID = 1L;
	/**
	 * 租户CODE
	 */
	private String tenantCode;
	/**
	 * 用户名称
	 */
	private String tenantName;
	/**
	 * 租户联系电话
	 */
	private String tenantPhone;
	/**
	 * 租户邮件
	 */
	private String tenantEmail;
	/**
	 * 租户联系地址
	 */
	private String tenantAddress;
	

	public String toString() {
		return "Model"+this.getClass().getName()+"[tenantCode=" + this.getTenantCode() + ",tenantName=" + this.getTenantName() + ",tenantPhone=" + this.getTenantPhone() + ",tenantEmail=" + this.getTenantEmail() + ",tenantAddress=" + this.getTenantAddress() + ",]";
	}	
}
