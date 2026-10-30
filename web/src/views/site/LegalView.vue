<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

interface Section {
  title: string
  items: string[]
}

interface Doc {
  title: string
  subtitle: string
  updatedAt: string
  sections: Section[]
}

const DOCS: Record<string, Doc> = {
  terms: {
    title: '用户协议',
    subtitle: '请在使用本平台前完整阅读并理解本协议全部条款',
    updatedAt: '2026-10-28',
    sections: [
      {
        title: '一、服务性质',
        items: [
          '本平台提供的是彩票开奖数据展示与创作者内容分享服务，属于信息与内容服务，不销售彩票，不代理任何彩票发行机构的销售业务。',
          '平台不提供购彩、代购、合买、资金跟投等功能，用户不得在本平台内进行任何形式的资金募集。',
          '平台展示的开奖号码来源于中国福彩网、中国体彩网等官方公开渠道，如与官方公告不一致，以官方公告为准。',
        ],
      },
      {
        title: '二、账号与实名',
        items: [
          '用户以手机号注册，须保证手机号为本人所有。账号仅限本人使用，不得出借、转让或出售。',
          '创作者须完成实名认证后方可发布付费内容；未满 18 周岁的用户不得充值，也不得成为创作者。',
          '用户应妥善保管账号密码，因用户自身原因导致账号被盗用的，平台不承担由此产生的损失。',
        ],
      },
      {
        title: '三、内容与审核',
        items: [
          '创作者发布的预测内容须为本人独立分析，不得抄袭、不得使用「必中」「包中」「稳赚」「保底」「内部消息」等承诺性表述。',
          '平台对发布内容实行先审后发，有权对违规内容下架、对账号限权或封禁，并保留追究责任的权利。',
          '用户可对违规内容举报，平台将在收到举报后及时核查处理。',
        ],
      },
      {
        title: '四、付费内容与命中判定',
        items: [
          '付费内容的价格由平台按统一公式计算，创作者不得私下改价或引导站外交易。',
          '命中判定以平台结构化预测项与官方开奖结果比对为准，判定口径在预测详情页公开，不接受以「感觉不准」为由的退款主张。',
          '创作者主动撤回或平台下架的内容，平台将按订单全额退还积分。',
        ],
      },
      {
        title: '五、协议变更与终止',
        items: [
          '平台有权根据法律法规与业务需要修订本协议，修订后在平台公示，公示期满继续使用即视为同意。',
          '用户可随时申请注销账号，注销后账号不可恢复，剩余积分按《积分规则》处理。',
        ],
      },
    ],
  },
  points: {
    title: '积分规则',
    subtitle: '积分是平台内的消费凭证，不具备货币属性',
    updatedAt: '2026-10-28',
    sections: [
      {
        title: '一、积分性质（重要）',
        items: [
          '积分仅可用于平台内消费（解锁付费预测、兑换站内权益），不可提现、不可转让、不可兑换现金，也不可在用户之间赠送。',
          '积分不是法定货币、不是预付卡余额、不具有任何投资或储值增值属性。',
          '平台不提供任何形式的积分兑现通道，任何声称可代兑积分的行为均与平台无关。',
        ],
      },
      {
        title: '二、获取方式',
        items: [
          '充值：1 元 = 10 积分，单笔 6–2000 元。',
          '首充赠送：首次充值额外赠送充值金额 10% 的等值积分，每个账号一次。',
          '每日签到：2 积分/天，连续签到满 7 天额外奖励 10 积分。',
          '邀请好友：好友通过你的邀请码注册，你获得 20 积分；好友完成首次充值，再获得 30 积分。',
          '新人礼包：注册即送 30 积分，有效期 7 天。',
          '创作者收益：付费内容被解锁后按结算规则计入积分。',
        ],
      },
      {
        title: '三、有效期',
        items: [
          '充值、签到、邀请等方式获得的积分有效期为 24 个月，新人礼包积分有效期为 7 天。',
          '消费时优先扣减最先到期的积分；到期未使用的积分由系统自动清零，不另行通知。',
          '积分过期属于正常规则，不构成退款理由。',
        ],
      },
      {
        title: '四、站内权益兑换',
        items: [
          '积分可兑换会员时长、实物礼品等站内权益，兑换后积分立即扣减。',
          '会员权益兑换即时生效；实物礼品由客服在 3 个工作日内联系确认收货信息，礼品以实物为准，不支持折现。',
          '实物礼品在未发货前可申请取消，积分原路退回；会员权益发放后不可撤销。',
        ],
      },
      {
        title: '五、退款与注销',
        items: [
          '充值后未消费的积分，可在注销账号时申请按原支付渠道退回，退款金额按 1 元 = 10 积分折算，已消费与已过期的积分不予退还。',
          '因平台原因导致内容无法访问的，平台按订单金额退还积分。',
        ],
      },
      {
        title: '六、风险提示',
        items: [
          '请理性消费，平台严禁未成年人充值。',
          '平台不对任何预测内容的准确性作出保证，购买付费内容属于内容消费行为，不等于投资。',
        ],
      },
    ],
  },
  disclaimer: {
    title: '免责声明',
    subtitle: '本平台内容仅供交流参考，不构成任何投注建议',
    updatedAt: '2026-10-28',
    sections: [
      {
        title: '一、不承诺结果',
        items: [
          '平台内所有预测内容均为创作者个人分析观点，不构成对开奖结果的预测承诺，也不构成任何投注建议。',
          '平台不使用、也不允许创作者使用「必中」「包中」「稳赚」「保底」「内部消息」等表述。',
          '彩票开奖为独立随机事件，历史命中率不代表未来表现。',
        ],
      },
      {
        title: '二、不构成赌博',
        items: [
          '平台不提供购彩、代购、合买、倍投计划、跟单返利、资金跟投等任何与赌博相关的功能。',
          '平台不展示盈亏、收益率、回报率等博彩化指标。',
          '用户因参与任何形式的彩票购买而产生的盈亏，与平台无关。',
        ],
      },
      {
        title: '三、数据来源',
        items: [
          '开奖数据来自中国福彩网、中国体彩网等官方公开渠道，平台按公开接口同步，如与官方公告不一致，以官方公告为准。',
          '平台不对第三方数据源的中断、延迟、错误承担责任，但会尽力修复。',
        ],
      },
      {
        title: '四、未成年人保护',
        items: [
          '本平台禁止未满 18 周岁的用户注册、充值与购买付费内容。',
          '实名认证环节会校验年龄，年龄不符的申请将被拒绝。',
          '如发现未成年人使用成年人账号消费，监护人可联系客服申请处理。',
        ],
      },
      {
        title: '五、责任限制',
        items: [
          '因不可抗力、网络故障、第三方服务中断导致的服务不可用，平台不承担赔偿责任。',
          '用户因违反本声明或相关法律法规产生的后果，由用户自行承担。',
        ],
      },
    ],
  },
}

const doc = computed<Doc>(() => DOCS[String(route.params.type)] ?? DOCS.terms!)
</script>

<template>
  <div class="legal">
    <header class="head">
      <h1>{{ doc.title }}</h1>
      <p class="sub">{{ doc.subtitle }}</p>
      <p class="meta">生效日期：{{ doc.updatedAt }}</p>
    </header>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="notice"
      title="以下为平台自拟条款文本，上线前须经法务审核确认后生效"
    />

    <section v-for="(section, index) in doc.sections" :key="index" class="block">
      <h2>{{ section.title }}</h2>
      <ul>
        <li v-for="(item, i) in section.items" :key="i">{{ item }}</li>
      </ul>
    </section>

    <footer class="foot">
      <router-link to="/legal/terms">用户协议</router-link>
      <router-link to="/legal/points">积分规则</router-link>
      <router-link to="/legal/disclaimer">免责声明</router-link>
    </footer>
  </div>
</template>

<style scoped>
.legal { max-width: 820px; margin: 0 auto; }
.head { margin-bottom: 16px; }
.head h1 { margin: 0 0 6px; font-size: 24px; }
.sub { margin: 0 0 4px; font-size: 13px; color: var(--text-sub); }
.meta { margin: 0; font-size: 12px; color: var(--text-sub); }
.notice { margin-bottom: 18px; }
.block { background: #fff; border: 1px solid var(--border-soft); border-radius: 12px; padding: 18px 20px; margin-bottom: 12px; }
.block h2 { margin: 0 0 10px; font-size: 15px; }
.block ul { margin: 0; padding-left: 18px; }
.block li { font-size: 13px; line-height: 1.95; color: var(--text-main); margin-bottom: 6px; }
.block li:last-child { margin-bottom: 0; }
.foot { display: flex; gap: 18px; justify-content: center; padding: 10px 0 4px; font-size: 13px; }
</style>
