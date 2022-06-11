package com.snow.system.mapper;

import java.util.List;
import com.snow.system.domain.SysOaLeave;

/**
 * 请假单Mapper接口
 * 
 * @author snow
 * @date 2020-11-22
 */
public interface SysOaLeaveMapper 
{
    /**
     * 查询请假单
     * 
     * @param id 请假单ID
     * @return 请假单
     */
    public SysOaLeave selectSysOaLeaveById(Integer id);

    /**
     * 查询请求单
     * @param leaveNo
     * @return
     */
    public SysOaLeave selectSysOaLeaveByLeaveNo(String leaveNo);
    /**
     * 查询请假单列表
     * 
     * @param sysOaLeave 请假单
     * @return 请假单集合
     */
    public List<SysOaLeave> selectSysOaLeaveList(SysOaLeave sysOaLeave);

    /**
     * 新增请假单
     * 
     * @param sysOaLeave 请假单
     * @return 结果
     */
    public int insertSysOaLeave(SysOaLeave sysOaLeave);

    /**
     * 修改请假单
     * 
     * @param sysOaLeave 请假单
     * @return 结果
     */
    public int updateSysOaLeave(SysOaLeave sysOaLeave);

    /**
     * 根据单号修改请假单
     * @param sysOaLeave
     * @return
     */
    public int updateSysOaLeaveByLeaveNo(SysOaLeave sysOaLeave);

    /**
     * 删除请假单
     * 
     * @param id 请假单ID
     * @return 结果
     */
    public int deleteSysOaLeaveById(Integer id);

    /**
     * 批量删除请假单
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteSysOaLeaveByIds(String[] ids);
}
