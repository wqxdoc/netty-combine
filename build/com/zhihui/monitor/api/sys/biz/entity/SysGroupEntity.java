package com.zhihui.monitor.api.sys.entity;

import com.heda.idss.base.vo.BaseModel;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SysGroupEntity extends BaseModel {

	private static final long serialVersionUID = 1L;
	/**
	 * 分组parent ID
	 */
	private Long groupParentId;
	/**
	 * 分组CODE
	 */
	private String groupCode;
	/**
	 * 分组CODE
	 */
	private String groupPcode;
	/**
	 * 分组名称
	 */
	private String groupName;
	/**
	 * level
	 */
	private Integer groupLevel;
	/**
	 * order
	 */
	private Integer groupOrder;
	/**
	 * 全路径
	 */
	private String groupTreePath;
	/**
	 * 名称全路径
	 */
	private String groupFullName;
	

	public String toString() {
		return "Model"+this.getClass().getName()+"[groupParentId=" + this.getGroupParentId() + ",groupCode=" + this.getGroupCode() + ",groupPcode=" + this.getGroupPcode() + ",groupName=" + this.getGroupName() + ",groupLevel=" + this.getGroupLevel() + ",groupOrder=" + this.getGroupOrder() + ",groupTreePath=" + this.getGroupTreePath() + ",groupFullName=" + this.getGroupFullName() + ",]";
	}	
}
