#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""独立学习/测试库的完整 HTTP 验证；创建虚构档案，禁止连接真实客户库。"""
import os, json, uuid, urllib.request, urllib.error, http.cookiejar
from decimal import Decimal
base=os.environ.get('BASE_URL','http://127.0.0.1:8097').rstrip('/')
password=os.environ['ADMIN_PASSWORD']
class Client:
    def __init__(self,username='admin',pwd=password):
        self.open=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.token=self.call('/auth/csrf')
        self.call('/auth/login','POST',{'username':username,'password':pwd})
    def call(self,path,method='GET',body=None,expected=200):
        headers={'Content-Type':'application/json'}
        if method!='GET':headers[self.token['header']]=self.token['token']
        request=urllib.request.Request(base+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            response=self.open.open(request,timeout=30);status=response.status;raw=response.read()
        except urllib.error.HTTPError as e:status=e.code;raw=e.read()
        assert status==expected,(method,path,status,raw.decode()[:300])
        return json.loads(raw)
checks=0
def check(value):
    global checks
    assert value;checks+=1
c=Client(os.environ.get('ADMIN_USERNAME','admin'));suffix=uuid.uuid4().hex[:8]
customer=c.call('/master/customers','POST',{'code':'QA-C-'+suffix,'name':'演示车主 / Fictional customer','contact':'demo@example.invalid','departmentId':1,'enabled':True})
vehicle=c.call('/master/vehicles','POST',{'plate':'QA-'+suffix,'model':'演示轿车 / Demo sedan','customerId':customer['id'],'odometer':48000,'departmentId':1,'enabled':True})
part=c.call('/master/parts','POST',{'code':'QA-P-'+suffix,'name':'演示机滤 / Demo filter','unit':'件 / piece','price':'45.00','reorderLevel':'5.000','departmentId':1,'enabled':True})
labor=c.call('/master/labor','POST',{'code':'QA-L-'+suffix,'name':'演示保养 / Demo service','unit':'次 / job','price':'120.00','departmentId':1,'enabled':True})
role=next(r for r in c.call('/lists/roles?size=100')['items'] if 'Technician' in r['name'])
tech=c.call('/admin/users','POST',{'username':'qa-tech-'+suffix,'displayName':'演示技师 / Demo technician','password':password,'roleId':role['id'],'departmentId':1,'enabled':True})
t=Client(tech['username'])
j=c.call('/jobs','POST',{'vehicleId':vehicle['id'],'odometer':48500,'complaint':'例行保养 / Routine maintenance','intakeNote':'外观与随车物品已登记 / Condition recorded'})
path='/jobs/'+str(j['id']);check(j['status']=='CHECKED_IN')
check(c.call('/jobs','POST',{'vehicleId':vehicle['id'],'odometer':49000,'complaint':'重复接车'},409)['code']=='VEHICLE_BUSY')
check(t.call(path,expected=403)['code']=='OUT_OF_SCOPE')
q=c.call(path+'/estimates','POST',{'note':'首次报价 / Initial estimate','lines':[{'kind':'LABOR','itemId':labor['id'],'quantity':'1.000','price':'120.00'},{'kind':'PART','itemId':part['id'],'quantity':'2.000','price':'45.00'}]})
eq='/estimates/'+str(q['id']);c.call(eq+'/submit','POST',{})
check(c.call(eq+'/accept','POST',{},400)['code']=='INVALID_INPUT')
c.call(eq+'/accept','POST',{'reference':'示例：电话确认记录 TEST-CONSENT / fictional consent reference'})
check(c.call(path)['authorized']==210)
c.call(path+'/assign','POST',{'technicianId':tech['id']});t.call(path+'/start','POST',{})
check(c.call(path)['job']['status']=='IN_PROGRESS')
lines=c.call(path)['lines'];p=next(l for l in lines if l['kind']=='PART');l=next(l for l in lines if l['kind']=='LABOR');lp='/lines/'+str(p['id'])
move={'quantity':'1.000','reference':'TEST-ISSUE','requestKey':uuid.uuid4().hex}
check(c.call(lp+'/issue','POST',move,409)['code']=='INSUFFICIENT_STOCK')
c.call('/parts/'+str(part['id'])+'/receive','POST',{'quantity':'10.000','price':'20.00','reference':'TEST-RECEIPT','requestKey':uuid.uuid4().hex})
c.call(lp+'/issue','POST',move);c.call(lp+'/issue','POST',move)
check(len(c.call(path)['movements'])==1)
source=c.call(path)['movements'][0]['id']
c.call(lp+'/return','POST',{'sourceId':source,'quantity':'1.000','reference':'TEST-RETURN','requestKey':uuid.uuid4().hex})
check(Decimal(str(c.call(path)['movements'][1]['inventoryValue']))==20)
c.call(lp+'/issue','POST',{'quantity':'1.000','reference':'TEST-ISSUE-2','requestKey':uuid.uuid4().hex})
# A separate additional estimate must obtain consent before any work.
q2=c.call(path+'/estimates','POST',{'note':'追加检查 / Additional check','lines':[{'kind':'LABOR','itemId':labor['id'],'quantity':'0.500','price':'120.00'}]})
check(c.call(path+'/quality','POST',{},409)['code']=='PENDING_AUTHORIZATION')
e2='/estimates/'+str(q2['id']);c.call(e2+'/submit','POST',{});c.call(e2+'/accept','POST',{'reference':'TEST-ADDITIONAL-CONSENT'})
for row in c.call(path)['lines']:t.call('/lines/'+str(row['id'])+'/complete','POST',{'note':'示例执行完成 / Fictional completion record'})
check(c.call(path)['gross']==225)
t.call(path+'/quality','POST',{});c.call(path+'/review','POST',{'passed':False,'note':'示例复检 / Demo recheck'})
check(c.call(path)['job']['status']=='IN_PROGRESS')
t.call(path+'/quality','POST',{});c.call(path+'/review','POST',{'passed':True,'note':'示例质检记录 / Fictional physical-check record'})
check(c.call(path+'/deliver','POST',{'reference':'TEST-HANDOVER'},409)['code']=='UNPAID')
payment={'amount':'100.00','reference':'TEST-RECEIPT-ERROR','requestKey':uuid.uuid4().hex}
c.call(path+'/pay','POST',payment);c.call(path+'/pay','POST',payment)
check(len(c.call(path)['payments'])==1)
source=c.call(path)['payments'][0]['id'];c.call(path+'/reverse','POST',{'sourceId':source,'reference':'TEST-CORRECTION','note':'示例录入纠错','requestKey':uuid.uuid4().hex})
check(c.call(path)['due']==225)
c.call(path+'/discount','POST',{'amount':'5.00'})
c.call(path+'/pay','POST',{'amount':'220.00','reference':'TEST-CASH-RECEIPT','requestKey':uuid.uuid4().hex})
check(c.call(path)['due']==0)
c.call(path+'/deliver','POST',{'reference':'TEST-HANDOVER: 演示领取 / fictional handover'})
check(c.call(path)['job']['status']=='DELIVERED')
check(len(c.call('/vehicles/'+str(vehicle['id'])+'/history'))==1)
check(t.call(path+'/pay','POST',{},403)['code']=='FORBIDDEN')
parts=c.call('/lists/parts?search='+part['code'])['items'][0];check(parts['quantity']==9 and parts['inventoryValue']==180)
check(any(r['id']==j['id'] and r['paid']==220 for r in c.call('/reports')['items']))
print(f'PASS: {checks} MySQL HTTP checks; intake, consent, additional estimate, stock/returns, review, payment reversal, handover, history and permissions')
