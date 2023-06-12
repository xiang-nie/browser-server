#
# XXL-JOB v2.3.1-SNAPSHOT
# Copyright (c) 2015-present, xuxueli.
use `xxl_job`;

INSERT INTO `xxl_job_info`(`job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`, `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`) VALUES (2, '更新地址内部转账交易数', '2021-12-06 17:06:33', '2021-12-06 17:06:33', 'admin', '', 'CRON', '0/30 * * * * ?', 'DO_NOTHING', 'FIRST', 'updateAddressTransferTxQtyJobHandler', '500', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'GLUE代码初始化', '2021-12-06 17:06:33', '', 0, 0, 0);

