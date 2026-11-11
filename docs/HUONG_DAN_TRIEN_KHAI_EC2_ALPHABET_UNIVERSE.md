# Triển khai Backend Alphabet Universe lên Amazon EC2

Ngày biên soạn: **09/11/2026**. Đối chiếu source tại D:/AWS/PJ_AWS_1/aws-workshop-main/backend.

**Mục tiêu:** tự chạy Spring Boot trên một EC2, dùng Cognito/S3/Polly hiện có, kết nối PostgreSQL Supabase, mở Swagger, kiểm tra lỗi và kiểm soát chi phí.

Đây là hướng dẫn để bạn tự thực hiện. Các lệnh mẫu **chưa được chạy trên AWS**; chưa tạo EC2, thay IAM, DB hoặc sửa source Java.

## Mục lục

- [0. Phạm vi và lựa chọn kiến trúc](#pham-vi)
- [1. EC2 có tốn tiền không?](#chi-phi)
- [2. Code cần sửa gì?](#source)
- [3. Chuẩn bị](#chuan-bi)
- [4. Tạo VPC, subnet và IGW](#vpc)
- [5. Tạo IAM Role](#iam)
- [6. Tạo Security Group](#sg)
- [7. Launch EC2](#launch)
- [8. SSH và cài Java](#ssh-java)
- [9. Build JAR](#build)
- [10. Upload và tổ chức thư mục](#upload)
- [11. Cấu hình ngoài JAR](#cau-hinh)
- [12. Chạy bằng systemd](#systemd)
- [13. Mở Swagger qua SSH tunnel](#tunnel)
- [14. Kiểm thử ứng dụng và FE](#kiem-thu)
- [15. Cập nhật và rollback](#cap-nhat)
- [16. Stop/start và dọn tài nguyên](#vong-doi)
- [17. Tìm lỗi](#tim-loi)
- [18. Mở API cho cả nhóm](#public-api)
- [19. Checklist và nguồn tham khảo](#checklist)

<a id="pham-vi"></a>

## 0. Phạm vi và lựa chọn kiến trúc

### Cấu hình mẫu

| Thành phần | Lựa chọn | Lý do |
|---|---|---|
| Region | us-east-1 — N. Virginia | CognitoConfig đang gọi us-east-1; ít cấu hình phải đổi khi học |
| OS | Amazon Linux 2023, x86_64, AMI chính thức Amazon | Có hướng dẫn EC2/Corretto trực tiếp của AWS |
| Instance | t3.small — 2 vCPU, 2 GiB RAM | Điểm bắt đầu cho lab Spring Boot/JPA; phải đo RAM thực tế |
| Purchase option | On-Demand, 1 instance | Dễ dừng/xóa, chưa cần cam kết dài hạn hay Spot interruption |
| Storage | Root EBS gp3 20 GiB, encrypted | OS, Java, JAR và log; media vẫn ở S3 |
| Network | Một public subnet, public IPv4 | Có outbound tới Supabase và các provider |
| Inbound | SSH 22 từ public IP của bạn/32 | Đủ đường quản trị và tunnel |
| Spring Boot | 127.0.0.1:8080 trên EC2 | Chỉ tiến trình trên máy và tunnel tới được |
| Swagger | Laptop 127.0.0.1:18080 qua SSH tunnel | Thử API qua SSH mã hóa, chưa public HTTP API |
| DB | Supabase hiện có hoặc bản sao lab | Tập trung thay nơi chạy Backend trước |
| AWS credentials | IAM Role gắn EC2 | SDK nhận credential tạm thời |
| Tiến trình | systemd, user riêng alphabet | Tự chạy khi boot, có trạng thái/log và restart khi lỗi |

**Giới hạn:** lab một máy có downtime khi cập nhật/reboot. Chưa có dự phòng nhiều AZ hoặc ingress HTTPS phục vụ công khai.

EC2 có thể ở Region khác Cognito; chọn cùng Region là quyết định đơn giản hóa, không phải yêu cầu bắt buộc. S3 client phải dùng Region thực tế của bucket. EC2 và DB Supabase ở xa nhau có thể tăng độ trễ.

### Sơ đồ

~~~mermaid
flowchart LR
    Laptop["Laptop: Browser / Postman / FE"]
    subgraph AWS["AWS"]
        Role["IAM Role"]
        subgraph VPC["VPC 10.20.0.0/16 - us-east-1"]
            IGW["Internet Gateway"]
            subgraph Subnet["Public subnet 10.20.1.0/24 - một AZ"]
                VM["EC2 Amazon Linux 2023"]
                App["Spring Boot 127.0.0.1:8080"]
            end
        end
        Cognito["Cognito"]
        S3["S3 ảnh / MP3"]
        Polly["Polly"]
    end
    DB[("PostgreSQL Supabase")]
    IPA["Merriam-Webster"]
    Laptop -->|"SSH 22 + port forwarding"| VM
    VM --> App
    Role -.->|"Credential tạm thời"| VM
    App -->|"Outbound"| IGW
    IGW -->|"HTTPS"| Cognito
    IGW -->|"HTTPS"| S3
    IGW -->|"HTTPS"| Polly
    IGW -->|"PostgreSQL TLS"| DB
    IGW -->|"HTTPS"| IPA
    Laptop -->|"Presigned URL: ảnh/audio"| S3
~~~

Port 8080 không có inbound public. Máy gọi ra ngoài nhờ public IPv4, route IGW và outbound phù hợp.

### Vì sao chưa chọn phương án khác?

| Phương án | Khi nào dùng | Lý do chưa dùng trong lab đầu |
|---|---|---|
| EC2 private + NAT | Backend private và egress riêng | Thêm NAT, route và phí trước khi quen EC2 |
| ALB + ACM | API HTTPS cho FE/team | Là giai đoạn sau khi app chạy ổn |
| RDS | Chuyển DB sang AWS | Cần migration/restore riêng; đổi cùng lúc khó phân biệt lỗi |
| Docker | Đóng gói môi trường, pipeline | Dùng JAR để hiểu Java/systemd trước |
| Auto Scaling | Thay máy và co giãn số instance | Cần launch template, health check, thiết kế trạng thái |
| Nginx | Reverse proxy/TLS trên máy | Tunnel đáp ứng kiểm thử cá nhân ở bước này |
| Elastic IP | IPv4 ổn định | Lab cập nhật IP sau start được; EIP giữ nhàn rỗi vẫn có phí |

<a id="chi-phi"></a>

## 1. EC2 có tốn tiền không?

**Có đơn giá. Số tiền thực trả phụ thuộc plan, Free Tier, credit, thời gian sử dụng và các tài nguyên đi kèm.**

### Trước khi Launch

1. AWS Console → Billing and Cost Management.
2. Xem Free Tier/plan, Credits và ngày hết hạn.
3. Xem Bills/Cost Explorer để biết các dịch vụ đang phát sinh.
4. Tạo Cost Budget phù hợp, ví dụ 5 USD cho đợt lab.
5. Cấu hình cảnh báo actual/forecast và kiểm tra email nhận.

Budget gửi cảnh báo **không tự tạo mức trần chi tiêu cứng**; dữ liệu billing có độ trễ. Tài liệu chưa đăng ký email/thông báo nào.

Nguồn: [Free Tier và plan](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier.html), [tạo Budget](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-create.html), [cách Budget hoạt động](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html).

### Khoản cần tính

| Khoản | Khi nào phát sinh |
|---|---|
| EC2 On-Demand | Instance running, kể cả không có người dùng |
| EBS | Dung lượng đã cấp phát, dù instance stopped |
| Public IPv4 | Theo thời gian địa chỉ được cấp/giữ |
| Snapshot | Khi tạo/giữ bản sao EBS |
| Data transfer | Theo hướng, Region và mức sử dụng |
| CPU surplus credits | Có thể có ở Unlimited mode khi vượt điều kiện bao gồm |
| S3/Polly/Cognito | Các lần test vẫn dùng các dịch vụ thật |
| ALB/NAT/CloudWatch bổ sung | Khi bạn tạo/bật các thành phần tương ứng |

VPC/subnet/route table/SG cơ bản không phải máy chủ tính phí theo giờ như EC2. IGW không có phí riêng; public IPv4, lưu lượng và thành phần mở rộng vẫn phải tính.

### Ví dụ ngân sách, không phải báo giá cố định

Tra cứu 09/11/2026:

- Trang T3 AWS hiển thị t3.small Linux/Unix us-east-1: **0.0209 USD/giờ**.
- Public IPv4 thông thường: **0.005 USD/giờ/địa chỉ**.
- Minh họa gp3 với mức **0.08 USD/GB-tháng**: 20 GB khoảng **1.60 USD/tháng**. Xác nhận giá Region/thông số thực tế trước khi tạo.

Quy ước minh họa 730 giờ/tháng:

~~~text
EC2:        0.0209 × 730 = 15.257 USD
1 IPv4:     0.005  × 730 =  3.650 USD
EBS gp3:    0.08   × 20  =  1.600 USD
Tổng cơ sở xấp xỉ        = 20.51 USD/tháng
~~~

Nếu chạy 2 giờ/ngày × 30 ngày, stop sau mỗi buổi nhưng giữ EBS cả tháng:

~~~text
EC2:        0.0209 × 60  = 1.254 USD
IPv4 tự cấp lúc running = 0.300 USD
EBS giữ cả tháng        = 1.600 USD
Tổng cơ sở xấp xỉ       = 3.15 USD/tháng
~~~

Chưa trừ credit/ưu đãi, chưa cộng thuế, lưu lượng, snapshot, API provider hoặc CPU surplus. Ví dụ sau dùng public IPv4 tự cấp được giải phóng khi stop, không dùng EIP giữ liên tục.

Nguồn: [T3](https://aws.amazon.com/ec2/instance-types/t3/), [public IPv4](https://aws.amazon.com/vpc/pricing/), [EBS](https://aws.amazon.com/ebs/pricing/), [On-Demand/CPU credits](https://aws.amazon.com/ec2/pricing/on-demand/). Dự toán lại bằng [AWS Pricing Calculator](https://calculator.aws/).

**T3 Standard trong lab:** tránh cơ chế tính CPU surplus của Unlimited, nhưng có thể bị giới hạn CPU khi hết credit. Đây là đánh đổi chi phí/hiệu năng, không phải bảo đảm bill bằng 0. [Standard mode](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-standard-mode.html).

<a id="source"></a>

## 2. Code cần sửa gì?

### Đã xác nhận trong source

| Thành phần | Ý nghĩa khi deploy |
|---|---|
| pom.xml: Boot 4.1.1, java.version=17 | Đóng gói executable JAR; bài này chọn runtime Corretto 21 |
| CognitoConfig | Region ghi trực tiếp us-east-1 |
| S3Config | Đọc aws.s3.region; client/presigner dùng credential chain mặc định |
| PollyConfig | Đọc aws.region; dùng credential chain mặc định |
| S3StorageServiceImpl | Cần aws.s3.bucket; prefix topic-images/ và vocabulary-audio/ |
| application.properties | Bị .gitignore bỏ qua; clone Git không bảo đảm có file |
| CorsConfig/SecurityConfig | Đã có CORS và bảo vệ JWT/local status |
| Auth logout | Ghép domain Cognito và FRONTEND_URL + /login |
| Clock/game timestamps | Có UTC; streak/leaderboard có quy tắc giờ Việt Nam riêng |

**Không bắt buộc sửa Controller/Service/Entity/schema chỉ để chạy trên EC2.** Bắt buộc cung cấp cấu hình runtime và kiểm tra JAR trên Java đã chọn. Đây chưa phải chứng nhận mọi API đã pass live EC2.

### File tạo trên EC2

| File đề xuất | Tác dụng |
|---|---|
| /etc/alphabet/application-ec2.properties | Cấu hình Spring, dùng placeholder |
| /etc/alphabet/backend.env | Giá trị DB/API key và môi trường, quyền đọc hạn chế |
| /etc/systemd/system/alphabet-backend.service | Cách chạy ứng dụng |
| /opt/alphabet/releases/backend-20261109-01.jar | Bản build |
| /opt/alphabet/backend.jar | Symlink tới bản đang chạy |
| /var/lib/alphabet | Working directory |

Đây là đường dẫn Linux đề xuất, chưa tạo trong source Windows.

### Thay đổi có điều kiện

- FE nối bản EC2 qua tunnel: đổi API base URL sang http://127.0.0.1:18080 rồi restart/rebuild FE theo cơ chế hiện tại.
- FE đổi origin: sửa CORS_ALLOWED_ORIGINS trên EC2.
- Mở API public: bổ sung HTTPS và bind address/SG phù hợp.
- Đổi Cognito Region: xử lý cả CognitoConfig, issuer, pool và app client; không chỉ AWS_REGION.
- Secrets Manager: cần cơ chế đọc secret riêng; EC2 không tự tích hợp dịch vụ đó.

**Git ignore không loại file khỏi JAR Maven.** File còn trong src/main/resources có thể được đóng gói. Config ngoài JAR không xóa secret đã nằm sẵn trong artifact; phải kiểm tra resources không chứa secret thật.

<a id="chuan-bi"></a>

## 3. Chuẩn bị

| Thông tin | Lấy ở đâu |
|---|---|
| Account ID | Menu tài khoản AWS |
| Pool ARN | Cognito → User pools → pool đang dùng |
| Pool ID | Source: us-east-1_i2fjAyM0t; đối chiếu môi trường thực |
| App Client ID | Client đang chạy đúng ở local |
| Cognito domain | Domain đầy đủ https://, không có / cuối |
| Bucket và Region | Bucket chứa ảnh/audio |
| DB URL/user/password | Môi trường test được nhóm xác nhận |
| Merriam-Webster key | Key đang dùng ở local |
| FE origin | Ví dụ http://localhost:5173 |
| Public IPv4 laptop | Rule SSH chọn My IP |

Không ghi secret/private key vào tài liệu chia sẻ.

**DB:** nếu EC2 trỏ DB hiện có, API ghi trên EC2 sẽ thay đổi chính DB đó. Chọn dữ liệu/tài khoản lab và sao lưu/bản sao phù hợp trước khi test. Bài này chưa migration RDS.

**Nơi chạy lệnh:**

- PowerShell — laptop: Windows của bạn.
- Bash — EC2: sau khi SSH vào Linux.
- json/properties/ini: nội dung dán vào đúng editor được chỉ dẫn.
- Thay giá trị THAY_... trước khi dùng.

<a id="vpc"></a>

## 4. Tạo VPC, subnet và IGW

Làm trên AWS Console. Nếu đã có mạng lab đúng cấu hình, dùng lại sau khi kiểm tra, không tạo trùng.

### 4.1. Chọn Region

US East (N. Virginia), us-east-1.

**Vì sao:** ít thay cấu hình trong bài đầu. Sau khi chạy được mới so độ trễ người dùng/DB và phương án Region khác.

### 4.2. VPC

1. VPC → Your VPCs → Create VPC.
2. Resources to create: VPC only.
3. Name: alphabet-lab-vpc.
4. IPv4 CIDR: 10.20.0.0/16; Tenancy: Default.
5. Tạo xong → Actions → Edit VPC settings.
6. Bật DNS resolution và DNS hostnames.

**Vì sao:** DNS phục vụ hostname provider/DB. VPC only giúp học từng thành phần và tránh wizard tự tạo NAT; nếu dùng wizard khác phải kiểm tra tài nguyên có phí.

### 4.3. Subnet

1. Subnets → Create subnet.
2. Chọn alphabet-lab-vpc.
3. Name: alphabet-lab-public-a.
4. Chọn một AZ; CIDR 10.20.1.0/24.
5. Tạo subnet.

**Vì sao:** EC2 nằm trong subnet thuộc một AZ; lab một máy chưa cần nhiều subnet.

### 4.4. IGW

1. Internet gateways → Create internet gateway.
2. Name: alphabet-lab-igw.
3. Actions → Attach to a VPC → alphabet-lab-vpc.

### 4.5. Route table

1. Route tables → Create route table.
2. Name: alphabet-lab-public-rt; đúng VPC.
3. Routes → Edit routes → giữ local, thêm 0.0.0.0/0 → IGW vừa tạo.
4. Subnet associations → Edit → chọn alphabet-lab-public-a.

| Destination | Target |
|---|---|
| 10.20.0.0/16 | local |
| 0.0.0.0/0 | igw-... của lab |

**Vì sao:** route table mới quy định đường đi; tên subnet public không tự tạo đường Internet. Giữ default NACL cho lab đầu, kiểm tra traffic cần thiết được phép.

Nguồn: [IGW](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html), [route table](https://docs.aws.amazon.com/vpc/latest/userguide/subnet-route-tables.html), [DNS](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-dns-updating.html).

<a id="iam"></a>

## 5. Tạo IAM Role

### 5.1. Tạo role EC2

1. IAM → Roles → Create role.
2. Trusted entity: AWS service; use case EC2.
3. Name: AlphabetUniverseEc2BackendRole.
4. Hoàn thành tạo role.
5. Mở role → Permissions → Add permissions → Create inline policy → JSON.

Console thiết lập trust cho EC2. Trust xác định ai được dùng role; policy xác định role được làm gì.

Danh tính đang thao tác Console còn cần quyền tạo/gắn role phù hợp, trong đó có iam:PassRole trên đúng role khi gắn vào EC2. Đây là quyền của người/pipeline triển khai, không phải lý do cấp thêm quyền IAM quản trị cho chính ứng dụng Backend. Nếu bị chặn ở bước attach profile, nhờ quản trị viên cấp đúng quyền triển khai. Xem [AWS về quyền sử dụng role với EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).

### 5.2. Policy theo source hiện tại

Thay THAY_BUCKET_NAME và THAY_ACCOUNT_ID trước khi lưu. Nếu pool môi trường khác thì thay cả pool ID.

~~~json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadWriteProjectMedia",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject"],
      "Resource": [
        "arn:aws:s3:::THAY_BUCKET_NAME/topic-images/*",
        "arn:aws:s3:::THAY_BUCKET_NAME/vocabulary-audio/*"
      ]
    },
    {
      "Sid": "GenerateEnglishSpeech",
      "Effect": "Allow",
      "Action": "polly:SynthesizeSpeech",
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws:RequestedRegion": "us-east-1"
        }
      }
    },
    {
      "Sid": "BlockUnblockProjectUsers",
      "Effect": "Allow",
      "Action": [
        "cognito-idp:AdminDisableUser",
        "cognito-idp:AdminEnableUser"
      ],
      "Resource": "arn:aws:cognito-idp:us-east-1:THAY_ACCOUNT_ID:userpool/us-east-1_i2fjAyM0t"
    }
  ]
}
~~~

6. Next → policy name AlphabetUniverseBackendMediaAndCognito → Create policy.

**Vì sao:**

- S3 giới hạn hai prefix đang dùng.
- Presigned GET cần quyền GetObject của danh tính ký URL.
- SynthesizeSpeech dùng Resource "*" cho thao tác tổng hợp; action vẫn chỉ là một thao tác, giới hạn Region.
- Hai lệnh Cognito Admin cần IAM; JWT Admin Swagger không thay thế IAM.
- Không cần cấp toàn bộ Cognito FullAccess cho các auth API công khai/token-authorized hiện có.
- Source storage đang đọc không gọi ListBucket/DeleteObject nên policy không tự cấp hai quyền đó. CLI liệt kê bucket có thể bị từ chối dù app đọc/ghi file đúng.
- Nếu bucket dùng SSE-KMS, kiểm tra quyền đúng KMS key; explicit deny từ bucket/boundary/SCP vẫn có thể chặn.

Role EC2 không tự nhận quyền IAM User trên laptop. Nếu đã sửa block ở user local, kiểm tra role EC2 cũng có quyền tương ứng.

Nguồn: [EC2 IAM Role](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html), [Java credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), [Polly IAM](https://docs.aws.amazon.com/polly/latest/dg/security_iam_service-with-iam.html), [Cognito AdminDisableUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminDisableUser.html).

<a id="sg"></a>

## 6. Tạo Security Group

1. EC2 → Security Groups → Create security group.
2. Name: alphabet-lab-backend-sg; chọn đúng VPC.
3. Inbound chỉ thêm:

| Type | Protocol | Port | Source |
|---|---|---|---|
| SSH | TCP | 22 | My IP, public IPv4 của bạn/32 |

4. Outbound giữ Allow all IPv4 outbound cho lab.
5. Tạo SG.

**Vì sao chỉ 22:** test qua SSH tunnel, chưa cần mở 8080/80/443. Không mở inbound 5432 vì PostgreSQL không chạy trên EC2 này.

**Vì sao outbound rộng:** app cần HTTPS, PostgreSQL, DNS và tải gói OS. Sau khi có dependency thực tế mới thu hẹp egress.

Nếu đổi Wi-Fi/VPN/public IP, cập nhật My IP. SG không nhận diện cố định “laptop của tôi”.

Nguồn: [SG rules](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html).

<a id="launch"></a>

## 7. Launch EC2

1. EC2 → Instances → Launch instances.
2. Name alphabet-lab-backend; tag Project=AlphabetUniverse, Environment=Lab, Owner=tên-bạn.
3. AMI: Amazon Linux 2023, x86_64, nguồn Amazon.
4. Instance type: t3.small.
5. Key pair → Create new:
   - Name alphabet-lab.
   - Type RSA, format .pem.
   - Tải về, giữ trên laptop, ví dụ C:\Users\ACER\.ssh\alphabet-lab.pem.
6. Network settings:
   - VPC alphabet-lab-vpc.
   - Subnet alphabet-lab-public-a.
   - Auto-assign public IP: Enable.
   - Existing SG: alphabet-lab-backend-sg.
7. Storage:
   - Root 20 GiB gp3, encrypted.
   - IOPS/throughput cơ bản.
   - Ghi lại Delete on termination.
8. Advanced details:
   - IAM instance profile: AlphabetUniverseEc2BackendRole.
   - Metadata endpoint enabled, IMDSv2/token required.
   - Credit specification: Standard cho lab nếu lựa chọn có sẵn; nếu sửa sau launch thì kiểm tra lại và đổi trong phần tương ứng của Console.
9. On-Demand, số lượng 1.
10. Review tài nguyên/giá rồi Launch.
11. Chờ running và các status check đạt; ghi Instance ID, Public IPv4.

**Vì sao:**

- t3.small là đề xuất khởi đầu, chưa benchmark app. RAM thiếu thì đo rồi nâng cấu hình.
- x86_64 đồng bộ AMI/type; chưa chuyển Arm khi chưa kiểm tra dependencies.
- gp3 chứa OS/JAR/log, không chuyển ảnh/audio khỏi S3.
- IMDSv2 phục vụ metadata/role credentials theo cơ chế token.
- User data không chứa password/key.
- Chọn AMI Marketplace có phí phần mềm sẽ làm dự toán khác.

Nguồn: [Launch EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html), [IMDSv2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html).

<a id="ssh-java"></a>

## 8. SSH và cài Java

### 8.1. Kết nối từ Windows

**PowerShell — laptop:**

~~~powershell
$KeyPath = 'C:\Users\ACER\.ssh\alphabet-lab.pem'
$EC2Address = 'THAY_PUBLIC_IPV4_CUA_EC2'
ssh -i $KeyPath "ec2-user@$EC2Address"
~~~

Amazon Linux dùng ec2-user; Ubuntu thường dùng ubuntu, không dùng lẫn.

Lần đầu đối chiếu host key/fingerprint qua thông tin kết nối EC2 theo AWS rồi chấp nhận đúng host key. Nếu báo private key permissions trên Windows, chỉnh ACL cho tài khoản sở hữu; không chia sẻ .pem hoặc upload lên EC2/repository.

**Bash — EC2:**

~~~bash
whoami
cat /etc/os-release
uname -m
~~~

Mong đợi ec2-user, Amazon Linux 2023 và x86_64.

### 8.2. Cài runtime

**Bash — EC2:**

~~~bash
sudo dnf update -y
sudo dnf install -y java-21-amazon-corretto-headless nano
java -version
free -h
df -h
~~~

**Vì sao:** headless không cần GUI; Java 21 là runtime được chọn cho bản target Java 17. Không đổi Boot/dependencies chỉ để làm deploy.

Nguồn: [Corretto 21 trên Amazon Linux](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/amazon-linux-install.html), [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html). Trang Spring có thể hiện bản mới hơn; pom dự án vẫn giữ 4.1.1.

### 8.3. Kiểm tra outbound/role

**Bash — EC2:**

~~~bash
curl --connect-timeout 10 --max-time 20 -fsS https://cognito-idp.us-east-1.amazonaws.com/us-east-1_i2fjAyM0t/.well-known/jwks.json -o /dev/null
~~~

Thành công chứng minh tới được endpoint JWKS công khai qua HTTPS; chưa chứng minh DB/Polly/quyền IAM đều đúng.

Nếu AWS CLI đã có:

~~~bash
aws --version
aws sts get-caller-identity --region us-east-1
~~~

Mong đợi ARN có assumed-role/AlphabetUniverseEc2BackendRole/....

Nếu thiếu CLI, xem [cài AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html). Java SDK không cần CLI mới dùng được role.

Không chép access key dài hạn hoặc ~/.aws/credentials từ laptop lên máy. Env/JVM properties có credential có thể được ưu tiên hơn instance role, nên xác minh danh tính thực tế nếu kết quả bất thường.

<a id="build"></a>

## 9. Build JAR

**PowerShell — laptop**, mở cửa sổ riêng:

~~~powershell
Set-Location -LiteralPath 'D:\AWS\PJ_AWS_1\aws-workshop-main\backend'
mvn -version
mvn -DskipTests clean package
Get-Item -LiteralPath '.\target\backend-0.0.1-SNAPSHOT.jar'
Get-FileHash -LiteralPath '.\target\backend-0.0.1-SNAPSHOT.jar' -Algorithm SHA256
~~~

**Vì sao build ở laptop:** không phải cài Maven/Git/build dependencies trên EC2 nhỏ; server chỉ cần runtime/artifact.

**Vì sao skipTests ở lệnh đóng gói:** có kiểm thử khởi động Spring có thể chạm DB khi chưa chuẩn bị môi trường. Lệnh vẫn compile test nhưng không chạy chúng; không được coi là toàn bộ test đã pass. Chạy suite phù hợp với DB test riêng khi kiểm thử.

pom.xml đang khai báo java.version=17. Maven dùng JDK 26 trên laptop không tự có nghĩa JAR cần Java 26; target vẫn cần kiểm tra theo pom. Có thể build/test bằng JDK 21 để đồng bộ runtime EC2.

Chỉ upload executable JAR đúng tên; không upload .jar.original, backend.zip, .idea, .env hoặc toàn bộ source.

Xem tên các file cấu hình có trong artifact:

~~~powershell
jar tf '.\target\backend-0.0.1-SNAPSHOT.jar' | Select-String -Pattern 'BOOT-INF/classes/.*\.(properties|yml|yaml|env)$'
~~~

Lệnh trên chỉ liệt kê entry, không tự xác minh nội dung đã sạch secret. Kiểm tra resources trước khi đóng gói. Application properties đã đọc đang tham chiếu các giá trị nhạy cảm bằng biến môi trường; phải rà lại nếu source thay đổi.

**Đạt khi:** BUILD SUCCESS, có JAR và mã SHA256 ghi lại.

<a id="upload"></a>

## 10. Upload và tổ chức thư mục

### 10.1. Upload

**PowerShell — laptop**, ở thư mục Backend:

~~~powershell
$KeyPath = 'C:\Users\ACER\.ssh\alphabet-lab.pem'
$EC2Address = 'THAY_PUBLIC_IPV4_CUA_EC2'
scp -i $KeyPath '.\target\backend-0.0.1-SNAPSHOT.jar' ('ec2-user@{0}:/home/ec2-user/backend-upload.jar' -f $EC2Address)
~~~

### 10.2. User và thư mục

**Bash — EC2:**

~~~bash
sha256sum /home/ec2-user/backend-upload.jar
id alphabet
~~~

Đối chiếu SHA256 với laptop. Nếu id alphabet báo chưa có user, tạo một lần:

~~~bash
sudo useradd --system --home-dir /var/lib/alphabet --shell /sbin/nologin alphabet
~~~

Sau đó:

~~~bash
sudo install -d -o root -g alphabet -m 0750 /opt/alphabet
sudo install -d -o root -g alphabet -m 0750 /opt/alphabet/releases
sudo install -d -o root -g alphabet -m 0750 /etc/alphabet
sudo install -d -o alphabet -g alphabet -m 0750 /var/lib/alphabet
sudo install -o root -g alphabet -m 0640 /home/ec2-user/backend-upload.jar /opt/alphabet/releases/backend-20261109-01.jar
sudo ln -sfn /opt/alphabet/releases/backend-20261109-01.jar /opt/alphabet/backend.jar
readlink -f /opt/alphabet/backend.jar
~~~

**Vì sao user riêng:** app không cần root/shell đăng nhập. Root thay JAR; app đọc artifact và ghi working data ở /var/lib/alphabet.

**Vì sao versioned JAR + symlink:** không ghi đè mất bản cũ, có đường rollback.

<a id="cau-hinh"></a>

## 11. Cấu hình ngoài JAR

### 11.1. File properties

**Bash — EC2:**

~~~bash
sudo nano /etc/alphabet/application-ec2.properties
~~~

Dán nội dung:

~~~properties
spring.application.name=backend
server.port=8080
server.address=127.0.0.1

spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=3MB

spring.security.oauth2.resourceserver.jwt.issuer-uri=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_i2fjAyM0t
aws.cognito.client-id=${AWS_COGNITO_CLIENT_ID}
aws.cognito.domain=${AWS_COGNITO_DOMAIN}
app.frontend-url=${FRONTEND_URL}

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.hikari.maximum-pool-size=3

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

merriam-webster.api-key=${MERRIAM_WEBSTER_API_KEY:}
aws.region=${AWS_REGION:us-east-1}
aws.s3.region=${AWS_S3_REGION:us-east-1}
aws.s3.bucket=${AWS_S3_BUCKET}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}
~~~

Nano: Ctrl+O → Enter để lưu, Ctrl+X để thoát.

**Vì sao:**

- Bind 127.0.0.1 khớp tunnel; mở SG 8080 cũng không làm app tự lắng nghe public IP.
- ddl-auto=none giữ quản lý schema hiện tại, không tự sửa DB khi boot.
- show-sql=false giảm log truy vấn; không thay nghiệp vụ.
- Pool=3 giữ cấu hình hiện tại; local và EC2 cùng chạy sẽ tăng tổng connection DB.
- Khai báo rõ aws.s3.bucket/region để không phụ thuộc env trong IntelliJ.
- Multipart giữ 2MB/3MB cho upload ảnh; không giới hạn trực tiếp MP3 Polly sinh nội bộ.

### 11.2. File môi trường

**Bash — EC2:**

~~~bash
sudo nano /etc/alphabet/backend.env
~~~

Dán mẫu rồi thay toàn bộ THAY_... bằng giá trị môi trường đã chọn:

~~~ini
AWS_REGION=us-east-1
AWS_S3_REGION=THAY_REGION_THUC_TE_CUA_BUCKET
AWS_S3_BUCKET=THAY_TEN_BUCKET

AWS_COGNITO_CLIENT_ID=THAY_APP_CLIENT_ID_DANG_DUNG
AWS_COGNITO_DOMAIN=https://THAY_DOMAIN.auth.us-east-1.amazoncognito.com

FRONTEND_URL=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

DB_URL="THAY_JDBC_POSTGRESQL_URL_CO_TLS"
DB_USERNAME="THAY_DB_USERNAME"
DB_PASSWORD="THAY_DB_PASSWORD"

MERRIAM_WEBSTER_API_KEY="THAY_MERRIAM_WEBSTER_API_KEY"
~~~

Đây là EnvironmentFile của systemd: **không thêm export**, không dán lệnh shell, không dùng ${DB_PASSWORD} để trỏ sang .env laptop. Giá trị phải có thật trên máy chủ.

Password có nháy/backslash/xuống dòng cần quoting đúng EnvironmentFile; không gửi secret vào chat. Trên EC2 xem man systemd.exec, mục EnvironmentFile.

### 11.3. Đối chiếu thông tin

- Cognito domain có https:// và không / cuối, vì source ghép /logout.
- FRONTEND_URL không / cuối; source ghép /login.
- Logout qua browser cần allowed sign-out URL của app client đúng, ví dụ http://localhost:5173/login.
- Không tạo client/pool mới chỉ để deploy nếu client cũ đang đúng.
- CORS_ALLOWED_ORIGINS là origin FE, không phải Backend; localhost khác 127.0.0.1.
- Thiếu Merriam key có thể làm IPA/tạo vocabulary lỗi dù EC2/S3/Cognito đúng.

### 11.4. Supabase DB

Giữ connection string đang dùng đúng ở local, có dạng JDBC PostgreSQL và TLS. Không dán postgres:// vào nơi driver yêu cầu jdbc:postgresql://.

Với EC2 IPv4-only, direct Supabase endpoint có thể cần IPv6; session pooler là lựa chọn khi cần IPv4. Lấy đúng host/port/username từ Dashboard → Connect. Không tự đổi sang transaction pooler vì ORM/prepared statements và cách pool khác.

Ví dụ định dạng, không phải DB thật:

~~~text
jdbc:postgresql://THAY_SESSION_POOLER_HOST:5432/postgres?sslmode=require
~~~

Nếu đã có verify-full hoạt động, giữ certificate/trust phù hợp. Chuẩn bị xác minh CA/hostname khi dùng cấu hình đó; không bỏ TLS để chữa lỗi.

Nguồn: [kết nối PostgreSQL Supabase](https://supabase.com/docs/guides/database/connecting-to-postgres).

### 11.5. File permissions

**Bash — EC2:**

~~~bash
sudo chown root:alphabet /etc/alphabet/application-ec2.properties
sudo chmod 640 /etc/alphabet/application-ec2.properties
sudo chown root:root /etc/alphabet/backend.env
sudo chmod 600 /etc/alphabet/backend.env
sudo ls -l /etc/alphabet
~~~

systemd hệ thống đọc EnvironmentFile trước khi chạy app bằng alphabet; user app không cần mở trực tiếp file secret.

File env hạn chế quyền là cách khởi đầu trong lab, không phải mã hóa secret hoặc tích hợp Secrets Manager.

Nguồn: [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html).

<a id="systemd"></a>

## 12. Chạy bằng systemd

### 12.1. Tạo unit

**Bash — EC2:**

~~~bash
sudo nano /etc/systemd/system/alphabet-backend.service
~~~

Dán:

~~~ini
[Unit]
Description=Alphabet Universe Spring Boot Backend
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
User=alphabet
Group=alphabet
WorkingDirectory=/var/lib/alphabet
EnvironmentFile=/etc/alphabet/backend.env
ExecStart=/usr/bin/java -Xms128m -Xmx768m -Duser.timezone=UTC -jar /opt/alphabet/backend.jar --spring.config.location=file:/etc/alphabet/application-ec2.properties
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
SuccessExitStatus=143
UMask=0027
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/alphabet
StandardOutput=journal
StandardError=journal
SyslogIdentifier=alphabet-backend

[Install]
WantedBy=multi-user.target
~~~

| Thiết lập | Vì sao |
|---|---|
| User/Group | App quyền hạn chế |
| EnvironmentFile | Không đưa secret vào ExecStart/source |
| WorkingDirectory | Không phụ thuộc thư mục SSH |
| spring.config.location | Chỉ định config runtime, thay các vị trí mặc định để tránh dùng nhầm file local trong JAR |
| Xmx768m | Giới hạn heap lab 2 GiB; vẫn phải tính native memory ngoài heap |
| user.timezone=UTC | JVM có quy ước rõ; các service vẫn dùng quy tắc giờ Việt Nam riêng |
| Restart=on-failure | Thử chạy lại khi tiến trình lỗi |
| WantedBy + enable | Chạy khi boot |
| PrivateTmp/ProtectSystem | Hạn chế nơi app được ghi |
| Journal output | Log qua journalctl, chưa phải đã gửi lên CloudWatch |

Sửa properties/env thì restart service. Sửa unit thì daemon-reload rồi restart.

Source có Clock UTC/timestamp UTC; không tự chuyển dữ liệu thời gian cũ trong DB khi deploy. Đối chiếu mốc ngày/tuần sau triển khai.

### 12.2. Chạy và kiểm tra

**Bash — EC2:**

~~~bash
sudo systemd-analyze verify /etc/systemd/system/alphabet-backend.service
sudo systemctl daemon-reload
sudo systemctl enable --now alphabet-backend
sudo systemctl status alphabet-backend --no-pager
sudo journalctl -u alphabet-backend -n 100 --no-pager
sudo ss -ltnp | grep ':8080'
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/v3/api-docs
~~~

Mong đợi:

- active (running), không lặp restart.
- Log Started BackendApplication.
- Lắng nghe 127.0.0.1:8080.
- GET /v3/api-docs trả 200.

pom.xml hiện không có Actuator; không dùng /actuator/health như endpoint chắc chắn tồn tại.

Nếu bị start-limit-hit, sửa nguyên nhân trước rồi:

~~~bash
sudo systemctl reset-failed alphabet-backend
sudo systemctl start alphabet-backend
~~~

Không chữa thiếu cấu hình bằng tắt JWT, permitAll mọi route hoặc cho Hibernate tự sửa schema.

Nguồn: [Spring Boot systemd](https://docs.spring.io/spring-boot/how-to/deployment/installing.html). Unit trên là mẫu riêng cho project, chưa thử trên EC2 live.

<a id="tunnel"></a>

## 13. Mở Swagger qua SSH tunnel

### 13.1. Mở tunnel

Mở PowerShell mới, giữ cửa sổ trong lúc test:

~~~powershell
$KeyPath = 'C:\Users\ACER\.ssh\alphabet-lab.pem'
$EC2Address = 'THAY_PUBLIC_IPV4_CUA_EC2'
ssh -i $KeyPath -N -L 127.0.0.1:18080:127.0.0.1:8080 -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 "ec2-user@$EC2Address"
~~~

Không có prompt sau kết nối thành công là bình thường với -N.

- 127.0.0.1:18080 đầu: listener trên laptop.
- 127.0.0.1:8080 cuối: đích nhìn từ EC2, là app trên EC2.
- -N: forward, không mở shell.
- ExitOnForwardFailure: báo lỗi nếu listener không tạo được.
- 18080 tránh đụng Backend local 8080.
- Dữ liệu sang EC2 đi trong SSH; không mở HTTP 8080 ra Internet.

Nguồn: [OpenSSH local forwarding](https://man.openbsd.org/ssh).

### 13.2. Mở URL

- Swagger: http://127.0.0.1:18080/swagger-ui/index.html
- OpenAPI: http://127.0.0.1:18080/v3/api-docs
- Postman base URL: http://127.0.0.1:18080

URL có 127.0.0.1 nhưng app ở EC2, nhờ tunnel. Đóng tunnel chỉ dừng đường truy cập laptop, không dừng EC2/app và chi phí.

Nếu Swagger Server URL sai port, xem /v3/api-docs và thử Postman trực tiếp port 18080 để phân biệt lỗi URL với Backend.

Nếu local cũng chạy Backend, đối chiếu request trong journalctl trên EC2 để tránh test nhầm bản.

<a id="kiem-thu"></a>

## 14. Kiểm thử ứng dụng và FE

### 14.1. Test đọc trước

| Kiểm tra | Kết quả cần thấy |
|---|---|
| GET /v3/api-docs | 200 |
| GET /api/users/me không token | 401 theo contract |
| Login ACTIVE hợp lệ | Token theo flow hiện tại |
| POST /api/auth/sync | User local xử lý đúng contract |
| GET /api/topics có token | Topic đúng dữ liệu |
| Learning/game questions | audioUrl gắn đúng vocabulary |
| Mở ảnh/audio URL | File thực tế đọc được qua HTTPS S3 |

Không gửi Bearer cũ/hết hạn khi thử Auth public; filter JWT có thể xử lý header trước luật permitAll.

### 14.2. Test ghi có kiểm soát

Trên dữ liệu/tài khoản lab đã xác nhận:

1. Tạo/cập nhật topic có ảnh → S3 PutObject.
2. Tạo vocabulary → IPA, Polly, upload MP3.
3. Tạo game/attempt → DB đúng, resume đúng.
4. Admin BLOCKED learner test → local BLOCKED và Cognito Disabled.
5. Login learner đó bị từ chối; ACTIVE lại rồi đăng nhập mới.
6. Kiểm tra history/progress/weekly scores/config audit theo nghiệp vụ.

Các thao tác này không do tài liệu tự chạy. Nếu dùng chung DB/S3/Cognito thì chúng thay đổi tài nguyên hiện có; chọn và dọn dữ liệu test đúng phạm vi.

### 14.3. CORS và FE

**PowerShell — laptop, tunnel đang mở:**

~~~powershell
curl.exe -i -X OPTIONS 'http://127.0.0.1:18080/api/users/me' -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: authorization'
~~~

Mong đợi preflight hợp lệ được xử lý, Access-Control-Allow-Origin khớp; không cần Bearer cho preflight đó.

Đặt API base URL FE sang http://127.0.0.1:18080 bằng config FE hiện tại. Nếu Vite nạp biến lúc build/start, restart dev server/rebuild. Không giả định tên biến cụ thể khi chưa mở config FE.

Chỉ laptop đang có tunnel mới dùng URL này; chưa phải endpoint HTTPS chung cho nhóm.

### 14.4. Reboot

**Bash — EC2:**

~~~bash
sudo reboot
~~~

SSH ngắt. Chờ máy lên, SSH lại:

~~~bash
sudo systemctl is-enabled alphabet-backend
sudo systemctl status alphabet-backend --no-pager
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/v3/api-docs
~~~

Mở lại tunnel. Đạt khi app tự lên, không cần gõ java -jar.

Theo dõi tài nguyên:

~~~bash
free -h
df -h
sudo journalctl --disk-usage
~~~

Log lớn thì đặt giới hạn/retention journald phù hợp máy lab; xem man journald.conf với SystemMaxUse/MaxRetentionSec. CloudWatch là bài tiếp theo cho log/metric tập trung.

<a id="cap-nhat"></a>

## 15. Cập nhật và rollback

### Cập nhật artifact

1. Build lại, chạy kiểm thử phù hợp.
2. Upload như Bước 10, so checksum.
3. Chọn tên release mới, ví dụ bản 02:

~~~bash
sudo install -o root -g alphabet -m 0640 /home/ec2-user/backend-upload.jar /opt/alphabet/releases/backend-20261109-02.jar
sudo systemctl stop alphabet-backend
sudo ln -sfn /opt/alphabet/releases/backend-20261109-02.jar /opt/alphabet/backend.jar
sudo systemctl start alphabet-backend
sudo systemctl status alphabet-backend --no-pager
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/v3/api-docs
~~~

### Rollback JAR

Chỉ khi bản cũ còn tương thích schema/config:

~~~bash
sudo systemctl stop alphabet-backend
sudo ln -sfn /opt/alphabet/releases/backend-20261109-01.jar /opt/alphabet/backend.jar
sudo systemctl start alphabet-backend
sudo journalctl -u alphabet-backend -n 100 --no-pager
~~~

Rollback JAR không tự rollback schema/dữ liệu. Giữ bản cũ để quay lại nhanh; lab một máy có downtime.

<a id="vong-doi"></a>

## 16. Stop/start và dọn tài nguyên

| Hành động | App | Điều cần nhớ |
|---|---|---|
| Đóng SSH/tunnel | systemd vẫn chạy | EC2 running vẫn tính phí |
| systemctl stop app | Chỉ app dừng | Máy vẫn running, vẫn có phí |
| Reboot EC2 | App lên lại nếu enabled | Không phải cách ngừng phí |
| Stop instance | App không phục vụ | Compute On-Demand dừng tính khi stopped; EBS còn phí |
| Start instance | systemd tự lên | Public IPv4 tự cấp thường đổi; cập nhật SSH/SCP/tunnel |
| Terminate | Máy bị xóa | EBS tùy DeleteOnTermination; snapshot/EIP khác cần kiểm tra |

Nguồn: [billing theo trạng thái](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-lifecycle.html), [stop/start](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Stop_Start.html), [EBS khi terminate](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/preserving-volumes-on-termination.html).

### Giữ máy cho buổi sau

1. Lưu kết quả.
2. Console → đúng EC2 có tag lab → Instance state → Stop instance.
3. Đợi stopped; ghi nhận EBS còn phí, EIP nếu có cần xét riêng.
4. Buổi sau Start, lấy IP mới nếu đổi, cập nhật My IP nếu laptop đổi mạng.
5. SSH và tunnel lại.

### Bỏ hẳn lab

1. Xác nhận đúng tài nguyên lab và phần dùng chung.
2. Lưu artifact/config cần thiết; backup có secret phải được bảo vệ.
3. Terminate đúng instance.
4. Kiểm tra EBS/snapshot/EIP còn giữ; chỉ xóa phần lab không cần dữ liệu.
5. Kiểm tra NAT/ALB vô tình tạo hoặc log có phí.
6. Gỡ role/policy lab nếu hết dùng.
7. Dọn SG/subnet/route, detach/xóa IGW rồi xóa VPC khi hết phụ thuộc.
8. Không xóa bucket ảnh/audio, pool Cognito hoặc DB dự án chỉ vì hết bài EC2.
9. Xem Bills/Cost Explorer sau đó vì dữ liệu có độ trễ.

<a id="tim-loi"></a>

## 17. Tìm lỗi

| Hiện tượng | Kiểm tra |
|---|---|
| SSH timeout | running → public IP mới → subnet/route IGW → SG My IP → NACL/VPN |
| Permission denied (publickey) | ec2-user đúng AMI, đúng .pem, ACL private key |
| Console báo iam:PassRole khi launch/gắn role | Kiểm tra quyền của danh tính triển khai trên đúng role; không cấp AdministratorAccess cho app để chữa lỗi này |
| Host key changed | Xác minh instance/host key qua AWS rồi cập nhật known_hosts; không tắt kiểm tra cho mọi host |
| systemd 203/EXEC | /usr/bin/java có đúng/được thực thi không |
| Unable to access jarfile | Symlink/JAR, quyền thư mục/group alphabet |
| Placeholder không resolve | Đúng tên biến trong EnvironmentFile, đã restart |
| Thiếu aws.s3.bucket | Cả property và AWS_S3_BUCKET ở Bước 11 |
| UnsupportedClassVersionError | Java runtime so với bytecode/dependencies, build target đúng |
| App killed/OOM | free -h, log kernel/JVM, heap/native memory; đo rồi tăng RAM nếu cần |
| DB timeout | DNS IPv4/IPv6, host/port, TLS, outbound/network restriction |
| DB password failed | Credential và username pooler; không phải SG |
| Too many DB connections | Tổng pool local + EC2 và hạn mức Supabase |
| S3 AccessDenied | Danh tính role, prefix, bucket policy, KMS nếu có |
| S3 sai Region | AWS_S3_REGION phải theo bucket |
| Polly lỗi | SynthesizeSpeech, Region, voice/engine, log nguyên nhân |
| Cognito ACCESS_DENIED | Role EC2 có hai action đúng ARN; IAM User laptop không tự sang role |
| Pool/client mismatch | Issuer, app client, pool và Region.US_EAST_1 của CognitoConfig |
| IPA 502/503 | Key Merriam, response upstream, outbound |
| Tunnel port in use | Đổi port local hoặc dừng đúng tunnel cũ |
| Tunnel connection refused | App trên EC2 chưa nghe 127.0.0.1:8080 |
| Public IP:8080 không vào được | Đúng với lab: loopback + không inbound 8080; dùng tunnel |
| API 401/403 | Token/role/status theo contract; không mở SG rộng để sửa xác thực |
| CORS | Origin thực tế, localhost khác 127.0.0.1, restart app/FE |
| Audio URL hết hạn | Lấy response mới; không dùng URL có hạn làm định danh file |
| Logout redirect lỗi | Domain https://, FRONTEND_URL và allowed sign-out URL |

**Bash — đọc log:**

~~~bash
sudo journalctl -u alphabet-backend -n 100 --no-pager
sudo journalctl -u alphabet-backend -f
~~~

Không chia sẻ log có password/token/URL chứa credential. Không in toàn bộ env/metadata credentials để kiểm tra role; dùng STS identity và lỗi cần thiết.

<a id="public-api"></a>

## 18. Mở API cho cả nhóm

Sau khi lab pass, thiết kế endpoint HTTPS chung:

~~~text
FE → https://api.ten-mien-cua-nhom → ALB + ACM → EC2 → DB
~~~

Khi chuyển ALB:

- Bổ sung hai subnet public ở hai AZ cho ALB thông thường.
- Có domain/certificate/DNS.
- Chọn vị trí Backend; private EC2 cần outbound tới provider.
- Đổi bind 127.0.0.1 thành địa chỉ phù hợp để ALB tới app.
- SG Backend chỉ nhận port ứng dụng từ SG ALB.
- Thiết kế health endpoint/security; source hiện chưa mặc định có Actuator health.
- FE dùng HTTPS chung; cập nhật CORS/callback/logout theo flow.
- Tính thêm ALB/public IPv4/NAT hoặc endpoint theo thiết kế.
- Test lại upload/audio/JWT và URL/forwarded headers sau proxy.

Không chỉ mở 8080 ra Internet rồi coi là đã hoàn thành endpoint đăng nhập cho nhóm. SSH tunnel là bước xác nhận app chạy trên AWS trước khi làm ingress HTTPS.

<a id="checklist"></a>

## 19. Checklist và nguồn tham khảo

### Đạt lab khi

- [ ] Đã xem plan/credit/dự toán và biết theo dõi phí.
- [ ] Giải thích được VPC/subnet/AZ/route/IGW.
- [ ] Đúng AMI/type/Region, có tag và IMDSv2.
- [ ] SSH nguồn tin cậy, 8080 chưa public trong lab.
- [ ] Role đúng bucket/prefix, Polly, Cognito.
- [ ] JAR upload đúng checksum.
- [ ] Config/env ngoài JAR đủ giá trị, file permissions đúng.
- [ ] systemd chạy user alphabet, tự lên sau reboot.
- [ ] Swagger/auth/ảnh/audio hoạt động qua tunnel.
- [ ] Chọn rõ DB/dữ liệu test.
- [ ] Biết stop/start, IP mới, cập nhật và rollback.
- [ ] Hiểu giới hạn một máy và chưa có HTTPS public ingress.

### Nguồn

Các link kỹ thuật/giá đã được tra cứu khi biên soạn. Console, giá và phiên bản tài liệu có thể thay đổi.

- [EC2 Getting Started](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html).
- [T3 và đơn giá](https://aws.amazon.com/ec2/instance-types/t3/).
- [EC2 On-Demand Pricing](https://aws.amazon.com/ec2/pricing/on-demand/).
- [Public IPv4 Pricing](https://aws.amazon.com/vpc/pricing/).
- [EBS Pricing](https://aws.amazon.com/ebs/pricing/).
- [Pricing Calculator](https://calculator.aws/).
- [Free Tier](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier.html).
- [EC2 IAM Role](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html).
- [Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/amazon-linux-install.html).
- [Spring external config](https://docs.spring.io/spring-boot/reference/features/external-config.html).
- [Spring systemd](https://docs.spring.io/spring-boot/how-to/deployment/installing.html).
- [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres).
- [OpenSSH](https://man.openbsd.org/ssh).

Chưa có xác nhận live từ tài liệu này. Ghi lại bằng chứng ở từng bước: tài nguyên, service status, mã HTTP, dữ liệu và chi phí thực tế.


