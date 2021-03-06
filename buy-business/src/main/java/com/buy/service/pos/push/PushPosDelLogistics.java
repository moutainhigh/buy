package com.buy.service.pos.push;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;

import com.buy.common.JsonMessage;
import com.buy.encryption.MD5Builder;
import com.buy.model.pos.PushPosRecord;
import com.buy.string.StringUtil;
import com.google.gson.Gson;
import com.jfinal.kit.PropKit;

public class PushPosDelLogistics {

	/*
	 * 参数
	 */

	static Logger L = Logger.getLogger(PushPosDelLogistics.class);

	//物流Id
	private Integer logisticId;

	//POS 推送商城更新物流信息
	public final static String REQ_PRO_DELETE = "deleteLogistics";


	public void push() {
		// 物流信息新增,修改,删除
		L.info("物流公司ID:"+logisticId);
		push(REQ_PRO_DELETE,logisticId);
	}


	/**
	 * 调用接口
	 */
	boolean push(String reqName,int logisticId) {
		if (StringUtil.isBlank(reqName) || StringUtil.isNull(logisticId)) {
			L.info("推送POS参数为空");
			return false;
		}

		/*
		 * 转化JSON
		 */
		List<HashMap<String, Object>> descList = new ArrayList<HashMap<String, Object>>();
		HashMap<String, Object> temp = new HashMap<String, Object>();
		temp.put("id", logisticId);
		descList.add(temp);
		String json = new Gson().toJson(descList);

		/*
		 * 打印JSON，并记录商品编号或名称
		 */
		String subject = "";

		// 物流公司信息更新
		if (REQ_PRO_DELETE.equals(reqName))
		{
			L.info("物流公司信息-JSON:" + json);
		}
		// 不存在接口
		else
		{
			L.info("调用接口名称：" + reqName + "（不存在）");
			return false;
		}

		/*
		 * 设置推送参数
		 */
		HashMap<String, String> paras = new HashMap<String, String>();
		String paraKey = PropKit.use("global.properties").get("push.pos.para.key");
		paras.put(paraKey, json);

		/*
		 * 处理推送数据
		 */
		int i = 0;
		NameValuePair[] data = new NameValuePair[1];
		StringBuffer sb = new StringBuffer();
		if (StringUtil.notNull(paras)) {
			data = new NameValuePair[paras.size() + 1];

			Object[] parasKeys = paras.keySet().toArray();
			Arrays.sort(parasKeys);

			Set<Entry<String, String>> set = paras.entrySet();
			Iterator<Entry<String, String>> it = set.iterator();

			while(it.hasNext()) {
				Entry entry = (Entry)it.next();
				String k = (String) entry.getKey();
				String v = (String) entry.getValue();
				
				data[i] = new NameValuePair(k, v);
				i++;
				
				if (!"sign".equals(k) && !"key".equals(k))
					sb.append(k + "=" + v + "&");
			}
			
			String key = PropKit.use("global.properties").get("push.pos.key");
			sb.append("key=" + key);
			String sign = MD5Builder.getMD5Str(sb.toString()).toUpperCase();
			i = i > 0 ? i : 0;
			data[i] = new NameValuePair("sign", sign);
		} else {
			L.info("没有商品参数，不推送");
			L.info("推送POS参数：" + sb.toString());
			return false;
		}
		
		/*
		 * 推送
		 */
		PushPosRecord error = null;
		JsonMessage jm = mainPush(reqName, data);
		
		/*
		 * 推送失败，再推送4次
		 */
		if (!"0".equals(jm.getStatus())) {
			int c = 0;
			while (c < 4) {
				jm = mainPush(reqName, data);
				if ("0".equals(jm.getStatus()))
					break;
				else
					c++;
			}
			
			if (!"0".equals(jm.getStatus()))
				error = new PushPosRecord().set("remark", jm.getMsg());
		}
		
		// 增加错误记录
		if (StringUtil.notNull(error)) {
			Date now = new Date();
			String reqResult = (String) jm.getData();
			
			error
				.set("request",			reqName)
				.set("type",			PushPosRecord.TYPE_LOGISTIC)
				.set("subject",			subject)
				.set("result_json",		reqResult)
				.set("create_time",		now)
				.set("update_time",		now)
				.save();
		}
		
		/*
		 * 推送结果
		 */
		L.info("推送POS接口：" + reqName);
		L.info("推送POS状态：" + jm.getStatus());
		L.info("推送POS数据：" + jm.getData());
		L.info("推送POS消息：" + jm.getMsg());
		return true;
	}
	
	static JsonMessage mainPush(String reqName, NameValuePair[] data) {
		JsonMessage result = new JsonMessage();
		String reqResult = null;
		
		try {
			String reqPrefix = PropKit.use("global.properties").get("push.pos.prefix");
			PostMethod post = new PostMethod(reqPrefix + reqName);
			post.addRequestHeader("Content-Type", "application/x-www-form-urlencoded; text/html; charset=utf-8");
			post.setRequestBody(data);
			
			HttpClient client = new HttpClient();
			client.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
			client.executeMethod(post);
			
			reqResult = new String(post.getResponseBodyAsString().getBytes());
			L.info("推送POS结果：" + reqResult);
			
			return new Gson().fromJson(reqResult, JsonMessage.class).setData(reqResult);
		} catch (Exception e) {
			e.printStackTrace();
			return result.setStatusAndMsg("-1", "请求出错");
		}
	}
	



	public PushPosDelLogistics setLogisticId(Integer logisticId) {
		this.logisticId = logisticId;
		return this;
	}



}
