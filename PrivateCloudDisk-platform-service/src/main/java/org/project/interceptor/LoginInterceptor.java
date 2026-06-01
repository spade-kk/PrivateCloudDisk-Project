package org.project.interceptor;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.project.util.JwtUtil;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.http.HttpHeaders;

/**定义一个拦截器*/
public class LoginInterceptor implements HandlerInterceptor {
    private JwtUtil jwtUtil;

    public LoginInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     *检测全局session对象中是否有uid数据,如果有则放行,如果没有重定向到登录页面
     * @param request 请求对象
     * @param response 响应对象
     * @param handler 处理器(把url和Controller映射到一块)
     * @return 返回值为true放行当前请求,反之拦截当前请求
     * @throws Exception
     */
    @Override
    //在DispatcherServlet调用所有处理请求的方法前被自动调用执行的方法
    //springboot会自动把请求对象给到request,响应对象给到response,适配器给到handler
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        //通过HttpServletRequest对象来获取session对象
//        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
//
//        if(authHeader == null || authHeader.startsWith("Bearer ")) {
//            //说明用户没有登录过系统,则重定向到login.html页面
//            //不能用相对路径,因为这里是要告诉前端访问的新页面是在哪个目录下的新
//            //页面,但前面的localhost:8080可以省略,因为在同一个项目下
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            //结束后续的调用
//            return false;
//        }
//
//        try {
//            String uid = jwtUtil.verifyAccessToken(authHeader.substring(7));
//        }
//        catch (JwtException ex) {
//            //令牌Token 无效或者过期等等
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            //结束后续的调用
//            return false;
//        }
        //放行这个请求
        return true;
    }
    //在ModelAndView对象返回给DispatcherServlet之后被自动调用的方法
//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
//    }
    //在整个请求所有关联的资源被执行完毕后所执行的方法
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//    }
}
