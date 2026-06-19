package org.project.control;

import org.project.control.result.JsonResult;
import org.project.model.vo.DashboardVO;
import org.project.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/business/admin/dashboard")
public class AdminDashboardController extends BaseController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @GetMapping
    public JsonResult<DashboardVO> getDashboard() {
        DashboardVO result = adminDashboardService.getDashboard();
        return new JsonResult<>(OK, result);
    }
}