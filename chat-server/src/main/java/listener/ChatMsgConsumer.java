package listener;

import com.alibaba.fastjson.JSON;
import model.chat.ChatMsg;
import properties.CommonPropertiesFile;
import properties.PropertiesMap;
import redis.clients.jedis.Jedis;
import session.ServerSession;
import session.ServerSessionMap;
import utils.CollectionUtil;
import utils.NodeUtil;
import utils.RedisUtil;
import utils.SendMsgUtil;

import java.util.List;

public class ChatMsgConsumer {
    /**
     * 监听当前节点的队列
     */
    public void start() {
        new Thread(() -> {
            consumer();
        }).start();
    }


    private void consumer() {
        while (true) {
            Jedis jedis = null;
            try {
                jedis = RedisUtil.getJedis();
                List<String> msg = jedis.brpop(Integer.MAX_VALUE, NodeUtil.node(CommonPropertiesFile.host, Integer.parseInt(PropertiesMap.getProperties("port"))), NodeUtil.node(CommonPropertiesFile.host, Integer.parseInt(PropertiesMap.getProperties("port"))));
                if (CollectionUtil.isEmpty(msg)) {
                    System.out.println("消费的消息为空");
                    continue;
                }
                if (msg.size() == 2) {
                    String msgString = msg.get(1);
                    // 解析消息并分发
                    System.out.println("来自其他集群的消息：" + msgString);
                    ChatMsg chatMsg = JSON.parseObject(msgString, ChatMsg.class);
                    if (checkUserLogin(chatMsg)) {
                        SendMsgUtil.sendMsg(chatMsg);
                        continue;
                    }
                    System.out.println(chatMsg.getToUid() + " 用户没有登录！");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                jedis.close();
            }

        }
    }


    /**
     * 检查用户channel是否在这个节点上，如果不在，表明这个用户真的没有登录
     *
     * @param chatMsg
     * @return
     */
    private boolean checkUserLogin(ChatMsg chatMsg) {
        Long toUid = chatMsg.getToUid();
        ServerSession session = ServerSessionMap.getSession(toUid);
        // 判断用户是否在本节点内有会话
        if (session != null && session.getChannel() != null) {
            return true;
        }
        return false;
    }

}
