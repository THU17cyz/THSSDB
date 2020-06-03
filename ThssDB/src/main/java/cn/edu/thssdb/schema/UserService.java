package cn.edu.thssdb.schema;
import cn.edu.thssdb.exception.CustomIOException;
import cn.edu.thssdb.exception.DataFileNotFoundException;
import cn.edu.thssdb.log.Logger;
import cn.edu.thssdb.operation.*;
import cn.edu.thssdb.parser.MyParser;
import cn.edu.thssdb.rpc.thrift.*;
import cn.edu.thssdb.transaction.TransactionManager;
import cn.edu.thssdb.transaction.TransactionStatus;
import cn.edu.thssdb.utils.Global;
import cn.edu.thssdb.utils.ShowTable;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/***************
 * [class] 用户服务
 ***************/
public class UserService {
    public User user;                                          // 用户数据
    
    // TODO
    private Logger logger;
    private TransactionManager transactionManager;
    /*
    private Thread serviceThread;                               // 服务线程
    private ReentrantLock lock = new ReentrantLock();           // 可重入锁
    private Condition condition = lock.newCondition();          // 条件对象
    */

    /**
     * [method] 构造方法
     */
    public UserService(User user) {
        this.user = user;
        try {
            transactionManager= new TransactionManager(user.database, null);
        } catch (Exception e) {
            throw new RuntimeException();
        }

        /*
        // 创建服务线程
        serviceThread = new Thread(this::handle);
        // 开始服务线程
        serviceThread.start();
         */
    }

    public void disconnect() {
        Manager.getInstance().getDatabaseByName(user.database).persist();
    }


    /**
     * [method] 服务处理方法 —— 主方法
     */
    public synchronized ExecuteStatementResp handle(String statement) {
        ExecuteStatementResp resp = new ExecuteStatementResp();
        List<List<List<String>>> data_all = new ArrayList<>();
        List<List<String>> columns_all = new ArrayList<>();
        List<String> title_all = new ArrayList<>();
        try {
            ArrayList<BaseOperation> operations = MyParser.getOperations(statement);
            for (BaseOperation operation: operations) {
                // 先设置operation操作的当前数据库
                operation.setCurrentUser(user.username, user.database);

                if (operation instanceof UseOperation) {
                    if (transactionManager.isUnderTransaction()) {
                        TransactionStatus status = transactionManager.exec(new CommitOperation());
                        if (!status.getStatus()) {
                            throw new RuntimeException(status.getMessage());
                        }

                    }
                    if (user.database != null) {
                        TransactionStatus status = transactionManager.exec(new CheckpointOperation());
                        if (!status.getStatus()) {
                            throw new RuntimeException(status.getMessage());
                        }
                    }
                    operation.exec();
                    String databaseName = ((UseOperation) operation).getName();
                    user.database = databaseName;
                    transactionManager.setDatabase(databaseName);
                } else if (operation instanceof CreateDatabaseOperation ||
                           operation instanceof CreateUserOperation ||
                           operation instanceof DropDatabaseOperation) {
                    operation.exec();
                } else {
                    TransactionStatus status = transactionManager.exec(operation);
                    if (!status.getStatus()) {
                        throw new RuntimeException(status.getMessage());
                    }
                    TransactionStatus.Table result = status.getRes();
                    if (result != null) {
                        title_all.add(result.title);
                        columns_all.add(result.columns);
                        data_all.add(result.data);
                    }
                }
            }
            if (title_all.size() > 0) {
                resp.setTableList(title_all);
                resp.setColumnsList(columns_all);
                resp.setRowList(data_all);
            }
            resp.setStatus(new Status(Global.SUCCESS_CODE));
            resp.setInformation(Global.SUCCESS_EXECUTE);
            resp.setIsAbort(true);
            resp.setHasResult(false);
            return resp;
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(new Status(Global.FAILURE_CODE));
            if (e.getMessage() == null) {
                resp.setInformation(e.toString());
            } else {
                resp.setInformation(e.getMessage());
            }

            resp.setIsAbort(true);
            resp.setHasResult(false);
            return resp;
        }



        // TODO 可根据类变量判断是否为完整事务，返回何值
    }
}
