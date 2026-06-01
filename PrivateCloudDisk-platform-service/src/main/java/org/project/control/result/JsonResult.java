package org.project.control.result;

import lombok.Data;
import org.project.service.ex.ServiceException;

import java.io.Serializable;

@Data
public class JsonResult<E> implements Serializable {
    //状态码
    private Integer code;
    //描述信息
    private String message;
    //数据类型不确定,用E表示任何的数据类型,一个类里如果声明的有泛型的数据类型,类也要声明为泛型
    private E data;

    //无参构造
    public JsonResult() {
    }

    //将状态码传给构造方法初始化对象
    public JsonResult(Integer code) {
        this.code = code;
    }

    //将状态码和数据传给构造方法初始化对象
    public JsonResult(Integer code, E data) {
        this.code = code;
        this.data = data;
    }

    //如果有异常,直接将异常传递给构造方法初始化对象
    public JsonResult(Throwable e) {
        this.message=e.getMessage();
    }

    //将状态码和描述信息传给构造方法初始化对象
    public JsonResult(String message, Integer code) {
        this.code=code;
        this.message=message;
    }
    /**以及属性的get和set方法*/
}
