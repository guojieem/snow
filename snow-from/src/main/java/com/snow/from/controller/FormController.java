package com.snow.from.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.snow.common.annotation.RepeatSubmit;
import com.snow.common.constant.CacheConstants;
import com.snow.common.constant.SequenceConstants;
import com.snow.common.core.domain.AjaxResult;
import com.snow.common.enums.FormFieldTypeEnum;
import com.snow.common.utils.CacheUtils;
import com.snow.common.utils.StringUtils;
import com.snow.flowable.common.constants.FlowConstants;
import com.snow.flowable.common.enums.FlowTypeEnum;
import com.snow.flowable.domain.*;
import com.snow.flowable.service.FlowableService;
import com.snow.flowable.service.FlowableTaskService;
import com.snow.framework.util.ShiroUtils;
import com.snow.from.domain.SysFormDataRecord;
import com.snow.from.domain.SysFormField;
import com.snow.from.domain.SysFormInstance;
import com.snow.from.domain.request.FormFieldRequest;
import com.snow.from.domain.request.FormRequest;
import com.snow.from.service.impl.SysFormDataRecordServiceImpl;
import com.snow.from.service.impl.SysFormFieldServiceImpl;
import com.snow.from.service.impl.SysFormInstanceServiceImpl;
import com.snow.from.util.FormUtils;
import com.snow.system.service.ISysSequenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author qimingjin
 * @Title:
 * @Description:
 * @date 2021/11/18 14:55
 */
@Controller
@RequestMapping()
@Slf4j
public class FormController{

    @Autowired
    private SysFormInstanceServiceImpl sysFormInstanceService;

    @Autowired
    private SysFormFieldServiceImpl sysFormFieldService;

    @Autowired
    private SysFormDataRecordServiceImpl sysFormDataRecordService;

    @Autowired
    private FlowableService flowableService;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private ISysSequenceService sequenceService;
    /**
     * ??????form????????????
     * @return ??????url??????
     */
    @GetMapping("formIndex")
    public String fromPreview() {
        return "formIndex";
    }

    @GetMapping("preview.html")
    public String preview() {
        return "preview";
    }

    @GetMapping("handwrittenSignature.html")
    public String handwrittenSignature() {
        return "handwrittenSignature";
    }

    @GetMapping("editorMenu.html")
    public String editorMenu() {
        return "editorMenu";
    }

    /**
     * ??????????????????
     * @param formRequest ????????????
     * @return ????????????
     */
    @PostMapping("/form/saveForm")
    @ResponseBody
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult saveForm(FormRequest formRequest){
        log.info("@@??????????????????????????????????????????:{}", JSON.toJSONString(formRequest));
        String formData = formRequest.getFormData();
        if(StrUtil.isBlank(formData)){
            return AjaxResult.error("???????????????????????????");
        }
        if(StringUtils.isEmpty(formRequest.getFormId())){
            return AjaxResult.error("??????id?????????");
        }
        if(StringUtils.isEmpty(formRequest.getFormName())){
            return AjaxResult.error("?????????????????????");
        }
        SysFormInstance sysFormInstanceCode = sysFormInstanceService.selectSysFormInstanceByFormCode(formRequest.getFormId());
        if(StringUtils.isNotNull(sysFormInstanceCode)){
            return AjaxResult.error(StrUtil.format("????????????:{}?????????",formRequest.getFormId()));
        }
        SysFormInstance sysFormInstanceName = sysFormInstanceService.selectSysFormInstanceByFormName(formRequest.getFormName());
        if(StringUtils.isNotNull(sysFormInstanceName)){
            return AjaxResult.error(StrUtil.format("????????????:{}?????????",formRequest.getFormName()));
        }
        //??????????????????
        SysFormInstance sysFormInstance=new SysFormInstance();
        sysFormInstance.setFormCode(formRequest.getFormId());
        sysFormInstance.setFormName(formRequest.getFormName());
        sysFormInstance.setRev(1L);
        sysFormInstance.setFromContentHtml(formData);
        sysFormInstance.setCreateBy(String.valueOf(ShiroUtils.getUserId()));
        sysFormInstance.setUpdateTime(new Date());
        sysFormInstanceService.insertSysFormInstance(sysFormInstance);
        //??????????????????
        saveFormField(sysFormInstance.getId(),formData);
        return AjaxResult.success();
    }

    /**
     * ??????
     * @return ?????????
     */
    @GetMapping("fromPreview")
    public String fromPreview(@RequestParam Long id, ModelMap mmap) {
        SysFormInstance sysFormInstance = sysFormInstanceService.selectSysFormInstanceById(id);
        mmap.put("formId",id);
        mmap.put("name",sysFormInstance.getFormName());
        return "fromPreview";
    }

    /**
     * ?????????????????????
     * @return ?????????????????????
     */
    @GetMapping("bindProcess")
    public String bindProcess(@RequestParam Long id, ModelMap mmap) {
        SysFormInstance sysFormInstance = sysFormInstanceService.selectSysFormInstanceById(id);
        mmap.put("sysFormInstance",sysFormInstance);
        return "system/instance/bindProcess";
    }
    /**
     * ????????????????????????
     * @return
     */
    @PostMapping("/form/saveFormRecord")
    @ResponseBody
    public AjaxResult saveFormRecord(@RequestParam String formId ,
                                     @RequestParam String formData,
                                     @RequestParam String formField){
        //????????????
        String formNo = sequenceService.getNewSequenceNo(SequenceConstants.OA_FORM_SEQUENCE);
        //?????????????????????????????????????????????
        String newFormData = FormUtils.fillFormFieldValue(formData, formField);
        Long userId = ShiroUtils.getUserId();
        SysFormDataRecord sysFormDataRecord=new SysFormDataRecord();
        sysFormDataRecord.setBelongUserId(String.valueOf(userId));
        sysFormDataRecord.setFormData(newFormData);
        sysFormDataRecord.setFormId(formId);
        sysFormDataRecord.setFormField(formField);
        sysFormDataRecord.setCreateBy(String.valueOf(userId));
        //?????????????????????
        Integer maxVersion = sysFormDataRecordService.getMaxVersionByUsrId(userId);
        //?????????+1?????????????????????
        sysFormDataRecord.setVersion(Optional.ofNullable(maxVersion).orElse(0)+1);
        sysFormDataRecord.setFormNo(formNo);
        sysFormDataRecordService.insertSysFormDataRecord(sysFormDataRecord);
        return AjaxResult.success();
    }

    /**
     * ???????????????
     * @param id ??????id
     * @param map ?????????????????????
     * @return ????????????
     */
    @GetMapping("/toFormRecordDetail")
    public String toFormRecordDetail(String id,ModelMap map){
        SysFormDataRecord sysFormDataRecord = sysFormDataRecordService.selectSysFormDataRecordById(Integer.valueOf(id));
        SysFormInstance sysFormInstance = sysFormInstanceService.selectSysFormInstanceById(Long.valueOf(sysFormDataRecord.getFormId()));
        map.put("id",id);
        map.put("name",sysFormInstance.getFormName());
        map.put("createTime",DateUtil.formatDateTime(sysFormDataRecord.getCreateTime()));
        return "formDetail";
    }


    /**
     * ????????????
     * @param id ????????????id
     * @return ????????????
     */
    @PostMapping("/form/getFormRecordDetail")
    @ResponseBody
    public AjaxResult getFormRecordDetail(Integer id){
        SysFormDataRecord sysFormDataRecord = sysFormDataRecordService.selectSysFormDataRecordById(id);
        return AjaxResult.success(sysFormDataRecord.getFormData());
    }

    /**
     * ????????????
     * @param id ????????????id
     * @return ??????????????????
     */
    @GetMapping("/form/startProcess")
    @ResponseBody
    @Transactional
    public AjaxResult startProcess(Integer id){
        SysFormDataRecord sysFormDataRecord = sysFormDataRecordService.selectSysFormDataRecordById(id);
        SysFormInstance sysFormInstance = sysFormInstanceService.selectSysFormInstanceById(Long.parseLong(sysFormDataRecord.getFormId()));
        StartProcessDTO startProcessDTO=new StartProcessDTO();
        startProcessDTO.setStartUserId(String.valueOf(ShiroUtils.getUserId()));
        startProcessDTO.setBusinessKey(sysFormDataRecord.getFormNo());
        startProcessDTO.setProcessDefinitionKey(sysFormInstance.getProcessKey());
        String formData=sysFormDataRecord.getFormData();
        String formField = sysFormDataRecord.getFormField();
        Map<String, Object> variables = Convert.toMap(String.class, Object.class, JSON.parse(formField));
        variables.put(FlowConstants.DF_FORM_ID,sysFormDataRecord.getFormId());
        variables.put(FlowConstants.FORM_DATA,formData);
        variables.put(FlowConstants.PROCESS_TYPE,FlowTypeEnum.FORM_PROCESS.getCode());
        startProcessDTO.setVariables(variables);
        ProcessInstance processInstance = flowableService.startProcessInstanceByKey(startProcessDTO);
        log.info("@@???????????????{},????????????id???{}",sysFormDataRecord.getFormNo(),processInstance.getId());
        return AjaxResult.success();
    }

    /**
     * ???????????????
     */
    @GetMapping("/createQRCode")
    public void createQRCode(@RequestParam("id") int id, HttpServletResponse response){
        Object domain = CacheUtils.getSysConfig(CacheConstants.SYS_DOMAIN, "http://localhost");
        QrConfig config = new QrConfig(500, 500);
        // ???????????????????????????????????????????????????
        config.setMargin(3);
        // ????????????????????????????????????????????????
        config.setForeColor(Color.CYAN);
        // ???????????????????????????
        config.setBackColor(Color.GRAY);
        config.setQrVersion(10);
       // config.setImg("https://qimetons.oss-cn-beijing.aliyuncs.com/45a22bcc93644dfe8bcacf690fe133f3.png");
        // ???????????????
        BufferedImage bufferedImage = QrCodeUtil.generate(StrUtil.format("{}/toFormRecordDetail?id={}",domain,id), config);
        try {
            //???PNG????????????????????????
            ServletOutputStream os = response.getOutputStream();
            ImageIO.write(bufferedImage, "PNG",os);
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new RuntimeException("?????????????????????");
        }
    }

    /**
     * ???????????????
     * @param taskId ??????id
     * @param mmap ????????????
     * @return ??????
     */
    @GetMapping("/toFinishTask")
    public String toFinishTask(String taskId,ModelMap mmap) {
        Task task =  flowableTaskService.getTask(taskId);
        HistoricProcessInstance historicProcessInstance = flowableService.getHistoricProcessInstanceById(task.getProcessInstanceId());
        Object formData = flowableService.getHisVariable(task.getProcessInstanceId(), FlowConstants.FORM_DATA);
        Object formId = flowableService.getHisVariable(task.getProcessInstanceId(), FlowConstants.DF_FORM_ID);
        if(ObjectUtil.isNotEmpty(formId)){
            SysFormInstance sysFormInstance = sysFormInstanceService.selectSysFormInstanceById(Long.parseLong(String.valueOf(formId)));
            mmap.put("name", sysFormInstance.getFormName());
        }
        mmap.put("taskId", taskId);
        mmap.put("businessKey", historicProcessInstance.getBusinessKey());
        mmap.put("processInstanceId", task.getProcessInstanceId());
        mmap.put("formData", String.valueOf(formData));
        return "formProcessDetail";
    }

    /**
     * ??????????????????
     * @param processInstanceId ????????????id
     * @return ????????????
     */
    @PostMapping("/form/getProcessFormData")
    @ResponseBody
    public AjaxResult getProcessFormData(String processInstanceId){
        Object formData = flowableService.getHisVariable(processInstanceId, FlowConstants.FORM_DATA);
        return AjaxResult.success(String.valueOf(formData));
    }

    /**
     * ????????????
     * @param finishTaskDTO ????????????
     */
    @PostMapping("/form/submitTask")
    @ResponseBody
    @RepeatSubmit
    public AjaxResult submitTask(FinishTaskDTO finishTaskDTO){
        finishTaskDTO.setUserId(String.valueOf(ShiroUtils.getUserId()));
        finishTaskDTO.setIsUpdateBus(true);
        flowableTaskService.submitTask(finishTaskDTO);
        return AjaxResult.success();
    }

    /**
     * ????????????????????????
     * @param taskId ??????id
     * @param modelMap ??????map
     * @return ????????????
     */
    @GetMapping("/getMyTaskedDetail")
    public String getMyHisTaskedDetail(String taskId,ModelMap modelMap) {
        //??????????????????
        HistoricTaskInstanceVO hisTask = flowableTaskService.getHisTask(taskId);
        Object formId = flowableService.getHisVariable(hisTask.getProcessInstanceId(), FlowConstants.DF_FORM_ID);
        if(ObjectUtil.isNotEmpty(formId)){
            SysFormInstance sysFormInstance = sysFormInstanceService.selectSysFormInstanceById(Long.parseLong(String.valueOf(formId)));
            modelMap.put("name", sysFormInstance.getFormName());
        }
        //??????????????????
        ProcessInstanceVO processInstanceVo = flowableService.getProcessInstanceVoById(hisTask.getProcessInstanceId());
        SysFormDataRecord sysFormDataRecord = sysFormDataRecordService.selectSysFormDataRecordByFormNo(processInstanceVo.getBusinessKey());
        modelMap.put("hisTask",hisTask);
        modelMap.put("appId",sysFormDataRecord.getId());
        modelMap.put("processInstance",processInstanceVo);
        return "/myTaskedDetail";
    }

    /**
     * ??????????????????????????????
     */
    @GetMapping("/startFormProcessDetail")
    @RequiresPermissions("system:flow:myStartProcessDetail")
    public String myStartProcessDetail(String processInstanceId,ModelMap modelMap) {
        ProcessInstanceVO processInstanceVo = flowableService.getProcessInstanceVoById(processInstanceId);
        HistoricTaskInstanceDTO historicTaskInstanceDTO=new HistoricTaskInstanceDTO();
        historicTaskInstanceDTO.setProcessInstanceId(processInstanceId);
        List<HistoricTaskInstanceVO> historicTaskInstanceList= flowableTaskService.getHistoricTaskInstanceNoPage(historicTaskInstanceDTO);
        SysFormDataRecord sysFormDataRecord = sysFormDataRecordService.selectSysFormDataRecordByFormNo(processInstanceVo.getBusinessKey());
        modelMap.put("historicTaskInstanceList",historicTaskInstanceList);
        modelMap.put("processInstanceId",processInstanceId);
        modelMap.put("processInstance",processInstanceVo);
        modelMap.put("appId",sysFormDataRecord.getId());
        return "/startFormProcessDetail";
    }
    /**
     * ??????????????????
     * @param formId ??????id
     * @param formData ????????????
     */
    private void saveFormField(Long formId,String formData ){
        //????????????
        JSONArray formDataArray = JSON.parseArray(formData);
        for(int i=0;i<formDataArray.size();i++){
            JSONObject fieldObject=formDataArray.getJSONObject(i);
            //??????????????????
            if(fieldObject.getString("tag").equals(FormFieldTypeEnum.GRID.getCode())){
                JSONObject gridObject = formDataArray.getJSONObject(i);
                JSONArray columnArray= gridObject.getJSONArray("columns");
                for(int j=0;j<columnArray.size();j++){
                    JSONObject columnObject = columnArray.getJSONObject(j);
                    JSONArray listArray = columnObject.getJSONArray("list");
                    for(int k=0;k<listArray.size();k++){
                        JSONObject listObject=listArray.getJSONObject(k);
                        FormFieldRequest formFieldRequest = listObject.toJavaObject(FormFieldRequest.class);
                        saveSysFormField(formFieldRequest,formId,JSON.toJSONString(listObject));
                    }
                }
            }
            //??????????????????
            else {
                FormFieldRequest formFieldRequest = fieldObject.toJavaObject(FormFieldRequest.class);
                saveSysFormField(formFieldRequest,formId,JSON.toJSONString(fieldObject));
            }
        }
    }

    /**
     * ??????
     */
    public void saveSysFormField( FormFieldRequest formFieldRequest,Long formId,String jsonString){
        SysFormField sysFormField = BeanUtil.copyProperties(formFieldRequest, SysFormField.class,"id");
        sysFormField.setFromId(formId);
        sysFormField.setFieldKey(formFieldRequest.getId());
        sysFormField.setFieldName(formFieldRequest.getLabel());
        sysFormField.setFieldType(formFieldRequest.getTag());
        sysFormField.setFieldHtml(jsonString);
        sysFormFieldService.insertSysFormField(sysFormField);
    }

}
