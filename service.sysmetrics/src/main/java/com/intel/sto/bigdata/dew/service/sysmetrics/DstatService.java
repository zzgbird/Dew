package com.intel.sto.bigdata.dew.service.sysmetrics;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.intel.sto.bigdata.dew.http.client.HttpStreamClient;
import com.intel.sto.bigdata.dew.message.ErrorMessage;
import com.intel.sto.bigdata.dew.message.ServiceResponse;
import com.intel.sto.bigdata.dew.service.Service;
import com.intel.sto.bigdata.dew.service.sysmetrics.message.CurrentDstatServiceRequest;
import com.intel.sto.bigdata.dew.service.sysmetrics.message.DstatServiceRequest;
import com.intel.sto.bigdata.dew.service.sysmetrics.message.HttpDstatServiceRequest;
import com.intel.sto.bigdata.dew.utils.Host;

public class DstatService extends Service {

  private boolean run = true;
  private DstatProcessor dp;

  @Override
  public void run() {
    String timeStamp = context.get(com.intel.sto.bigdata.dew.utils.Constants.MASTER_CURRENT_TIME);
    if (!Constants.USE_MASTER_TIME || timeStamp == null) {
      timeStamp = String.valueOf(System.currentTimeMillis());
    }
    dp = DstatProcessor.getInstance(timeStamp);
    dp.startThread();
    while (run) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public ServiceResponse get(Object message) {
    ServiceResponse result = new ServiceResponse();
    if (dp == null) {
      result.setEm(new ErrorMessage("Service is stoped."));
      return result;
    }
    if (message instanceof CurrentDstatServiceRequest) {
      String content = dp.getCurrentDstat();
      result.setContent(content);
      return result;
    }
    if (!(message instanceof DstatServiceRequest)) {
      result.setEm(new ErrorMessage("Wrong request message type, except DstatServiceRequest."));
      return result;
    }
    DstatServiceRequest request = (DstatServiceRequest) message;

    String content;
    try {
      content = dp.findWorkloadMetrics(request.getStartTime(), request.getEndTime());
    } catch (Exception e) {
      result.setEm(new ErrorMessage(e.getMessage()));
      return result;
    }
    if (message instanceof HttpDstatServiceRequest) {
      HttpDstatServiceRequest httpRequest = (HttpDstatServiceRequest) message;
      Map<String, String> parameters = new HashMap<String, String>();
      parameters.put(Constants.HOST_NAME, Host.getName());
      // TODO use FileInputStream or other InputStream to avoid build large String.
      InputStream is =
          new ByteArrayInputStream((content + System.getProperty("line.separator")).getBytes());
      try {
        HttpStreamClient.post(httpRequest.getHttpUrl(), parameters, is);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      result.setContent(content);
    }
    return result;
  }

  @Override
  public void stop() {
    run = false;
    dp.kill();
    dp = null;
  }

}
