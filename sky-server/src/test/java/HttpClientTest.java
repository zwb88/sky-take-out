package com.sky;
import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//@SpringBootTest
public class HttpClientTest {
    /**
     * 测试通过HttpClient的方式发送Get请求
     * @throws Exception
     */
    @Test
    public void testGet () throws Exception{


        //创建HttpClient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();

        //创建请求对象

        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");

        //发送请求接收响应结果

        CloseableHttpResponse response = httpClient.execute(httpGet);


        //获取服务端返回的状态码

        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("服务端返回的状态码为：" + statusCode);

        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("服务端返回的数据为："+ body);

        //关闭服务
        response.close();
        httpClient.close();


    }
@Test
public void testPost()throws Exception{

        //创建HttpClient对象
    CloseableHttpClient httpClient = HttpClients.createDefault();

    //创建请求对象

    HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");

    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("username","admin");
    jsonObject.addProperty("password","123456");


   StringEntity entity = new StringEntity(jsonObject.toString());
   //设置编码格式
   entity.setContentEncoding("UTF-8");
   //设置数据格式
   entity.setContentType("application/json");

    httpPost.setEntity(entity);

    //发送请求就收响应结果
    CloseableHttpResponse response = httpClient.execute(httpPost);

    //解析返回的数据-状态码和数据
    int statusCode = response.getStatusLine().getStatusCode();
    System.out.println("服务端返回的状态码："+ statusCode);

    HttpEntity entity1 = response.getEntity();
    String body = EntityUtils.toString(entity1);
    System.out.println("服务端返回的数据："+ body);


    //关闭服务

    response.close();
    httpClient.close();

    }

}
