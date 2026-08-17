------

## **CloudFlow DSL 示例**

```cloudflow
workflow "weekly_sales_report" {

    metadata {

        display_name = "销售周报"

        description = """
        每周一自动生成销售周报
        收集销售数据
        分析统计
        生成Excel报告
        保存到企业空间
        通知相关人员
        """

        version = "1.0"
    }


    trigger {

        schedule {

            cron = "0 8 * * 1"

            timezone = "Asia/Shanghai"

        }

    }


    runtime {

        timeout = 30m

        max_parallel = 4

        retry_policy {

            max_attempts = 3

            strategy = "exponential"

        }

    }



    variables {


        sales_node_id = input.string(
            required = true
        )


        template_file_id = input.string(
            required = true
        )


        report_node_id = input.string(
            required = true
        )

    }



    step collect_files {


        name = "收集销售文件"


        action file.list {


            node = vars.sales_node_id


            filter {


                extension = "xlsx"


            }


        }


        output excel_files


    }





    step aggregate_data {


        name = "销售数据统计"


        depends_on collect_files



        action data.aggregate_excel {



            input {

                files = collect_files.output


            }


            group_by = "region"



            metrics {


                sum("sales")


                average("profit")


            }


        }



        output report_data


    }





    step generate_report {



        name = "生成销售报告"


        depends_on aggregate_data



        condition {


            aggregate_data.output.row_count > 0


        }



        action plugin {



            id = "8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7"



            function = "generate_report"



            version = "1"



            input {


                data = aggregate_data.output


                template = vars.template_file_id


            }



        }



        retry {


            max_attempts = 2


            backoff = exponential


        }



        output report_file


    }






    step save_report {



        name = "保存报告"


        depends_on generate_report



        action file.save {


            source = generate_report.output.file_id


            target = vars.report_node_id


        }


    }






    on_failure {



        step notify_failure {



            action notification.send {


                channel = "user"



                title = "销售周报生成失败"



                message = """

                失败步骤:

                ${workflow.failed_step}

                """

            }

        }

    }

}
```

------

# **这个 DSL 和 YAML 最大区别在哪里？**

你的 YAML：

```yaml
steps:
 - id: generate_report
   uses: plugin.xxx
   if: xxx
```

它只是描述数据。

但是 CloudFlow DSL：

```cloudflow
step generate_report {

    depends_on aggregate_data


    condition {

        aggregate_data.output.row_count > 0

    }


    retry {

        max_attempts=2

    }

}
```

它表达的是：

一个具有生命周期、依赖关系、条件、异常策略的业务节点。

------

# **如果增加复杂能力，DSL优势更加明显**

例如：

## **1. 条件分支**

YAML：

很难表达。

CloudFlow：

```cloudflow
if report_data.total_sales > 100000 {


    step approve {


        action approval.request {

            users=[
                "manager"
            ]

        }

    }


}
else {


    step auto_publish {


        action file.publish {}

    }


}
```

编译后：

```json
{
 "type":"condition",
 "expression":"report_data.total_sales > 100000",
 "branches":[
   {
    "condition":"true",
    "steps":[]
   },
   {
    "condition":"false",
    "steps":[]
   }
 ]
}
```

------

# **2. 循环**

比如：

每个区域生成一个报告。

DSL:

```cloudflow
foreach region in report_data.regions {


    step create_region_report {


        action plugin.generate_excel {


            input={

                region=region

            }


        }


    }

}
```

IR:

```json
{
"type":"foreach",

"collection":"report_data.regions",

"body":[

 {
   "action":"plugin.generate_excel"
 }

]

}
```

------

# **3. 并行执行**

例如：

生成：

- PDF
- Excel
- PPT

DSL:

```cloudflow
parallel {


    step excel {


        action report.excel {}

    }



    step pdf {


        action report.pdf {}

    }



    step ppt {


        action report.ppt {}

    }

}
```

Runtime知道：

三个任务没有依赖关系。

可以同时执行。

------

# **4. 错误捕获**

例如：

生成失败：

重新生成。

超过次数：

发送通知。

DSL:

```cloudflow
try {


    step generate {


        action plugin.report.create {}

    }



}


catch error {


    step notify {


        action user.notify {


            message="报告生成失败"

        }


    }


}
```

------

# **5. 人工审批节点**

企业非常需要。

例如：

财务报表：

生成 → 审核 → 发布。

DSL:

```cloudflow
step approval {


    action human.approval {


        approvers=[

            "finance_manager"

        ]


        timeout=24h


    }


}
```

Runtime：

暂停 Workflow 状态：

```
RUNNING

↓

WAITING_APPROVAL

↓

APPROVED

↓

CONTINUE
```

------

# **CloudFlow DSL 最终编译结果**

用户写：

```
CloudFlow DSL
```

↓

Parser

↓

AST

↓

Semantic Analyzer

↓

IR

例如：

```json
{
 workflow:"weekly_sales_report",

 trigger:{
    type:"schedule",
    cron:"0 8 * * 1"
 },


 nodes:[

 {
   id:"collect_files",

   type:"task",

   action:"file.list"

 },


 {
   id:"aggregate",

   type:"task",

   action:"data.aggregate",

   depends:["collect_files"]

 },


 {
   id:"generate",

   type:"plugin",

   action:"generate_report",

   depends:["aggregate"]

 }

 ]

}
```

↓

Workflow Runtime

↓

Workflows Scheduler Service

↓

MQ / Event Bus / HTTP

↓

Plugin Runtime