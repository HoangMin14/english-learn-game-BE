# Lộ trình học 12 dịch vụ AWS cho Alphabet Universe

Ngày biên soạn: **05/11/2026**.

Mục tiêu: hiểu lý thuyết, tự giải thích và vẽ architecture, làm được lab từng dịch vụ, sau đó ghép thành hệ thống chạy ứng dụng học tiếng Anh.

Tài liệu tham chiếu nguồn chính thức AWS; hướng dẫn công cụ vẽ dùng tài liệu của draw.io. Các lab dưới đây là **bài tập đề xuất cho dự án**, không phải cam kết rằng mọi cấu hình đều miễn phí hay đã được triển khai trên tài khoản của bạn.

Theo source đã xem trước đó, ứng dụng đã tích hợp trực tiếp **Cognito, S3 và Polly**. Những phần còn lại trong tài liệu là kiến trúc dự kiến để học và triển khai.

## Mục lục

- [Cách học và chuẩn bị](#chuan-bi)
- [Bản đồ 12 dịch vụ và thứ tự học](#ban-do)
- [1. IAM — quyền truy cập AWS](#iam)
- [2. VPC — mạng của hệ thống](#vpc)
- [3. EC2 — máy chủ Backend](#ec2)
- [4. S3 — lưu file](#s3)
- [5. RDS PostgreSQL — database](#rds)
- [6. Secrets Manager — thông tin bí mật](#secrets)
- [7. CloudWatch — theo dõi và tìm lỗi](#cloudwatch)
- [8. Cognito — tài khoản và đăng nhập](#cognito)
- [9. Polly — phát âm tiếng Anh](#polly)
- [10. ACM — chứng chỉ HTTPS](#acm)
- [11. ELB / ALB — cửa vào API](#alb)
- [12. CloudFront — phân phối Frontend](#cloudfront)
- [Cách vẽ architecture](#ve-so-do)
- [Lab tổng hợp Alphabet Universe](#lab-tong-hop)
- [Lịch học và tiêu chí hoàn thành](#lich-hoc)
- [Tra lỗi thường gặp](#tra-loi)
- [Dọn tài nguyên sau lab](#don-tai-nguyen)

<a id="chuan-bi"></a>

## Cách học và chuẩn bị

### Học một dịch vụ theo 5 bước

1. Đọc mục lý thuyết và mở link tài liệu tương ứng.
2. Viết lại bằng lời của mình: dịch vụ giải quyết vấn đề gì, đầu vào và đầu ra là gì.
3. Vẽ sơ đồ nhỏ trước khi tạo tài nguyên.
4. Làm lab, kiểm tra cả trường hợp thành công và trường hợp lỗi.
5. Lưu bằng chứng, ghi điều đã hiểu, dọn tài nguyên không dùng.

Một bài chỉ xem là hoàn thành khi bạn có thể **tự làm lại từ đầu và giải thích lý do chọn cấu hình**.

### Nền tảng cần biết

- Region là khu vực triển khai; Availability Zone (AZ) là vùng hạ tầng riêng trong một Region.
- HTTP/HTTPS, DNS, IP, port; hiểu sơ bộ CIDR như 10.20.0.0/16.
- Phân biệt authentication — xác thực danh tính và authorization — kiểm tra quyền.
- Biết chạy JAR Spring Boot, xem log, dùng Git và biến môi trường.
- Biết SQL cơ bản, transaction, primary key, foreign key, unique constraint.

Nguồn học nền:

- [AWS Cloud Practitioner Essentials](https://explore.skillbuilder.aws/learn/courses/134/aws-cloud-practitioner-essentials): học tổng quan AWS trước; kiểm tra lựa chọn ngôn ngữ khi mở khóa học.
- [AWS Skill Builder](https://aws.amazon.com/training/digital/): tìm khóa nhập môn theo tên dịch vụ. Một số bài/lab cần đăng nhập hoặc gói học.
- [AWS Educate](https://aws.amazon.com/education/awseducate/): tìm nội dung và môi trường thực hành dành cho người mới.
- [Danh mục AWS Workshops](https://builder.aws.com/build/workshops): lọc dịch vụ, trình độ và điều kiện cấp sandbox; không mặc định mọi workshop có sẵn tài khoản thực hành.

### Chuẩn bị môi trường lab

1. Dùng môi trường thử nghiệm riêng; dùng dữ liệu giả cho email, tài khoản và database.
2. Bật MFA cho tài khoản quản trị; dùng danh tính có quyền phù hợp cho công việc thường ngày.
3. Chọn một Region chính và ghi lại. Khi học, có thể dùng Region đang chứa Cognito của dự án để giảm việc cấu hình chéo Region.
4. Đặt tên dễ nhận diện: alphabet-lab-ec2, alphabet-lab-db; gắn tag Project=AlphabetUniverse, Environment=Lab, Owner=tên-bạn.
5. Lập danh sách tài nguyên được tạo và chi phí dự kiến trước khi bấm Create.

**Chi phí:** kiểm tra quyền lợi Free Tier/credit của chính tài khoản; không áp dụng các con số miễn phí trong bài cũ một cách máy móc. Dùng [AWS Pricing Calculator](https://calculator.aws/) và [hướng dẫn tạo AWS Budget](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-create.html).

Budget thông thường gửi cảnh báo, **không tự đặt một mức trần khiến mọi dịch vụ dừng ngay**. Số liệu và thông báo có độ trễ; đọc [cách AWS Budgets theo dõi chi phí](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html). Đặc biệt theo dõi EC2, EBS, RDS, ALB, NAT Gateway, public IPv4, log và lưu lượng.

<a id="ban-do"></a>

## Bản đồ 12 dịch vụ và thứ tự học

| Bài | Dịch vụ | Câu hỏi nó giải quyết | Áp dụng vào Alphabet Universe |
|---|---|---|---|
| 1 | AWS IAM | Thành phần nào được phép thao tác tài nguyên AWS? | Cấp quyền cho Backend gọi S3, Polly, Cognito Admin và đọc secret |
| 2 | Amazon VPC | Máy chủ và database nằm trong mạng nào? | Subnet, route, Security Group cho Backend/DB |
| 3 | Amazon EC2 | Chạy chương trình Backend ở đâu? | Chạy Spring Boot |
| 4 | Amazon S3 | File ảnh, MP3 và bản build FE lưu ở đâu? | Ảnh topic, audio và file tĩnh React |
| 5 | Amazon RDS for PostgreSQL | Dữ liệu quan hệ lưu ở đâu? | Lựa chọn chuyển database từ Supabase sang RDS |
| 6 | AWS Secrets Manager | Mật khẩu DB và API key được quản lý ở đâu? | Mật khẩu DB, key Merriam-Webster |
| 7 | Amazon CloudWatch | Khi hệ thống chậm/lỗi thì xem ở đâu? | Log Spring Boot, metric, alarm |
| 8 | Amazon Cognito | Người học đăng ký/đăng nhập và nhận token thế nào? | Auth, refresh, quản trị khóa/mở tài khoản |
| 9 | Amazon Polly | Chuyển từ tiếng Anh thành âm thanh thế nào? | Tạo MP3 phát âm từ vựng |
| 10 | AWS Certificate Manager (ACM) | HTTPS dùng chứng chỉ nào? | Chứng chỉ cho domain API và FE |
| 11 | Elastic Load Balancing — Application Load Balancer | Request API được đưa tới máy chủ nào? | Listener HTTPS, target group và health check |
| 12 | Amazon CloudFront | Phân phối web tới trình duyệt thế nào? | CDN cho React từ S3 |

Đây là **12 chủ đề chính đã chọn**, không phải toàn bộ tài nguyên có thể xuất hiện trong hóa đơn. ALB là một loại load balancer của Elastic Load Balancing; Security Group và IAM Role thuộc các dịch vụ tương ứng. EBS, NAT Gateway hoặc cơ chế mã hóa bên dưới có thể phát sinh khi làm lab.

Thứ tự học đề xuất:

~~~text
IAM → VPC → EC2 → S3 → RDS → Secrets Manager → CloudWatch
    → Cognito → Polly → ACM → ALB → CloudFront → Lab tổng hợp
~~~

<a id="iam"></a>

## 1. AWS IAM — quyền truy cập AWS

### Lý thuyết cần nắm

IAM kiểm soát quyền thao tác tài nguyên AWS. Học các khái niệm principal, policy, role, trust policy, permission policy, ARN, action, resource và explicit deny. Đọc [IAM là gì](https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction.html).

Ứng dụng trên EC2 có thể nhận credential tạm thời qua **IAM Role và instance profile**. SDK dùng credential đó để ký request; không cần đặt access key dài hạn trong source. Đọc [cấp quyền cho ứng dụng chạy trên EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).

Áp dụng: Backend cần quyền trên bucket/prefix dùng cho ảnh và audio, quyền Polly, quyền Cognito Admin trên đúng User Pool và quyền đọc secret cần thiết. IAM không thay thế kiểm tra learner/admin trong database.

### Cách đưa vào sơ đồ

Vẽ IAM Role gắn với EC2 bằng đường nét đứt, nhãn “quyền cho ứng dụng”. Vẽ các lệnh S3/Polly/Cognito bằng đường request riêng; dữ liệu API không đi xuyên qua một máy chủ tên IAM.

### Lab 01 — đọc được file đúng quyền

**Chuẩn bị:** hoàn thành bước tạo EC2 ở Bài 3 và bucket ở Bài 4 khi thực hành.

1. Tạo role cho EC2, trust policy cho phép dịch vụ EC2 sử dụng role.
2. Cấp quyền đọc một object/prefix thử nghiệm trong bucket lab.
3. Gắn role qua instance profile.
4. Dùng SDK hoặc CLI trên EC2 tải object được phép.
5. Thử ghi object khi role chỉ có quyền đọc: phải bị từ chối.
6. Ghi lại action/resource cần thêm nếu bài toán yêu cầu upload.

**Đạt khi:** giải thích được “trust policy cho ai dùng role” và “permission policy cho role làm gì”; đọc thành công nhưng ghi bị AccessDenied.

**Tự hỏi:** vì sao mở Security Group không sửa được lỗi IAM AccessDenied? Vì sao không cấp AdministratorAccess cho ứng dụng chỉ cần đọc MP3?

**Dọn lab:** tháo role và xóa policy/role lab khi không còn tài nguyên phụ thuộc.

<a id="vpc"></a>

## 2. Amazon VPC — mạng của hệ thống

### Lý thuyết cần nắm

Học CIDR, subnet, AZ, route table, Internet Gateway, NAT Gateway, public/private IP và DNS. Một subnet thuộc một AZ. Public subnet có route ra Internet Gateway; một EC2 muốn dùng IPv4 trực tiếp qua gateway còn cần public IPv4 và luật mạng phù hợp. Đọc [VPC là gì](https://docs.aws.amazon.com/vpc/latest/userguide/what-is-amazon-vpc.html).

Security Group kiểm soát traffic vào/ra tài nguyên và có tính stateful. Học thêm sự khác nhau với Network ACL. Xem [hướng dẫn Security Group](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html).

Backend trong private subnet cần đường outbound để gọi Cognito, Polly, Merriam-Webster và tải gói cài đặt. Mô hình IPv4 thường dùng NAT Gateway; endpoint AWS chỉ giải quyết dịch vụ được hỗ trợ, không thay Internet cho Merriam-Webster. Tham khảo [VPC có private subnet và NAT](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-example-private-subnets-nat.html).

### Cách đưa vào sơ đồ

Vẽ AWS Cloud → Region → VPC → AZ → subnet → EC2/RDS. Ghi CIDR và route. S3/Cognito/Polly không được vẽ như máy chủ nằm trong subnet của bạn.

### Lab 02 — tự thiết kế mạng

1. Vẽ VPC 10.20.0.0/16.
2. Tạo hai public subnet: 10.20.1.0/24 ở AZ-A và 10.20.2.0/24 ở AZ-B.
3. Tạo hai private subnet: 10.20.11.0/24 và 10.20.12.0/24 ở hai AZ.
4. Gắn Internet Gateway, cấu hình route cho public subnet.
5. Lab đầu có thể đặt EC2 trong public subnet, giới hạn truy cập theo IP của bạn.
6. Bài nâng cao chuyển EC2 vào private subnet và thiết lập outbound; tính chi phí NAT trước.
7. Kiểm tra EC2 truy cập HTTPS ra ngoài và kiểm tra kết nối DB nội bộ.

**Đạt khi:** giải thích được vì sao “có Internet Gateway” chưa đủ để mọi EC2 vào Internet; phân biệt timeout mạng với lỗi quyền AWS.

**Dọn lab:** xóa tài nguyên phụ thuộc trước subnet/VPC; NAT Gateway và địa chỉ IPv4 cần được kiểm tra riêng.

<a id="ec2"></a>

## 3. Amazon EC2 — máy chủ Backend

### Lý thuyết cần nắm

EC2 cung cấp máy chủ ảo. Học AMI, instance type, vCPU/RAM, kiến trúc CPU, EBS, key pair, user data và vòng đời start/stop/terminate. Bạn vẫn quản lý hệ điều hành, Java và tiến trình ứng dụng. Đọc [EC2 là gì](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/concepts.html) và [lab khởi tạo EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html).

Áp dụng: JAR Spring Boot chạy trên EC2. Chọn Java theo bản build và dependencies thực tế; không mặc định máy cloud phải dùng đúng JDK đang cài trên laptop. Kiểm tra RAM cho JVM và connection pool DB.

### Cách đưa vào sơ đồ

Vẽ EC2 trong subnet, ghi “Spring Boot :8080”, hệ điều hành/Java nếu cần. Vẽ EBS gắn EC2; IAM Role là quan hệ cấp quyền. Khi có ALB, đường API là ALB → EC2.

### Lab 03 — chạy được dịch vụ và khởi động lại

1. Tạo EC2 Linux lab; chọn cấu hình phù hợp ngân sách và kiến trúc ứng dụng.
2. Gắn role, giới hạn SSH theo IP hiện tại nếu dùng SSH.
3. Chạy một dịch vụ HTTP mẫu trước để xác nhận mạng.
4. Sau đó triển khai bản build Backend với cấu hình lab, database lab và các giá trị môi trường cần thiết.
5. Cấu hình tiến trình bằng systemd; kiểm tra tự chạy lại sau reboot.
6. Gọi API bằng Postman; API được bảo vệ vẫn phải yêu cầu token.
7. Quan sát CPU/RAM và log khi gửi nhiều request có giới hạn.

**Đạt khi:** reboot máy vẫn khởi động ứng dụng; phân biệt lỗi tiến trình chết, sai port và Security Group.

**Tự hỏi:** stop khác terminate thế nào? Nếu chỉ có một EC2 và máy hỏng, ALB có tự tạo máy thay thế không? Đáp án cho ý sau: cần cơ chế bổ sung như Auto Scaling; ALB tự nó không tạo Backend.

**Dọn lab:** terminate instance thử nghiệm và kiểm tra EBS, snapshot, Elastic IP còn lại.

<a id="s3"></a>

## 4. Amazon S3 — lưu file

### Lý thuyết cần nắm

S3 lưu object trong bucket; object key xác định file, prefix tạo cách tổ chức tên. Học metadata, Content-Type, bucket policy, Block Public Access, versioning và lifecycle. Đọc [S3 là gì](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) và [lab tạo bucket, upload, download](https://docs.aws.amazon.com/AmazonS3/latest/userguide/GetStartedWithS3.html).

Presigned URL cấp quyền tạm thời cho một thao tác trên object. URL có thể hết hiệu lực sớm hơn thời gian yêu cầu nếu credential ký URL hết hạn. Xem [presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html).

Áp dụng: database lưu key ảnh/audio; Backend tạo URL truy cập khi trả response. Bản build React có thể nằm ở bucket FE riêng.

### Cách đưa vào sơ đồ

Vẽ S3 ngoài VPC, ghi rõ bucket FE và bucket media. Mũi tên upload từ Backend; mũi tên tải MP3 từ trình duyệt tới S3 qua URL đã ký.

### Lab 04 — MP3 riêng tư nhưng người học vẫn nghe được

1. Tạo bucket lab riêng tư, giữ Block Public Access.
2. Upload một MP3 và ảnh mẫu; thiết lập Content-Type phù hợp.
3. Lưu key thử nghiệm như vocabulary-audio/lab/sugar.mp3.
4. Mở URL không ký trong cửa sổ chưa xác thực: không được đọc file riêng tư.
5. Tạo presigned GET URL thời hạn ngắn, mở và nghe.
6. Đợi URL hết hạn rồi yêu cầu URL mới.
7. Thử key sai, quyền GetObject thiếu và file không còn tồn tại; ghi lại cách phân biệt.

**Đạt khi:** giải thích được key khác URL thế nào; không dùng URL có hạn làm định danh lâu dài trong DB.

**Lưu ý:** CORS không cấp quyền đọc object. Học CloudFront ở Bài 12 trước khi public hóa web từ bucket.

**Dọn lab:** xóa object, version cũ và delete marker nếu đã bật versioning, rồi mới xóa bucket lab.

<a id="rds"></a>

## 5. Amazon RDS for PostgreSQL — database

### Lý thuyết cần nắm

RDS quản lý hạ tầng database; bạn vẫn chịu trách nhiệm schema, truy vấn và nghiệp vụ. Học DB instance, endpoint, DB subnet group, backup, snapshot, restore, Single-AZ, Multi-AZ và read replica. Đọc [RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html).

DB subnet group thông thường cần subnet ở ít nhất hai AZ, kể cả khi DB Single-AZ. Điều đó không có nghĩa bạn đã có hai DB instance. Xem [RDS trong VPC](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html).

Kết nối PostgreSQL dùng TLS; học xác minh CA/hostname, không chỉ bật mã hóa. Xem [TLS cho RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).

### Cách đưa vào sơ đồ

Vẽ DB trong private DB subnet; ghi PostgreSQL :5432. Vẽ kết nối từ EC2, không từ trình duyệt. Chỉ vẽ standby/read replica khi thực sự chọn cấu hình đó.

### Lab 05 — database riêng tư và phục hồi được

1. Làm theo [lab tạo và kết nối RDS PostgreSQL từ EC2](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_GettingStarted.CreatingConnecting.PostgreSQL.html).
2. Chọn DB lab không public; Security Group DB cho phép 5432 từ Security Group Backend.
3. Tạo dữ liệu giả và một bảng có ID tự sinh, unique constraint, foreign key.
4. Kết nối từ EC2 qua TLS; máy ngoài đường mạng được cấp không kết nối được.
5. Tạo snapshot, phục hồi sang DB lab mới và đối chiếu dữ liệu.
6. Bài nâng cao: chuyển bản sao schema/data dự án theo [hướng dẫn import PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Procedural.Importing.html).

**Đạt khi:** sau restore, dữ liệu và lần INSERT tiếp theo đúng; kiểm tra cả sequence/identity, index, function và trigger audit.

**Lưu ý dự án:** đổi JDBC URL không tự chuyển dữ liệu Supabase. Kiểm tra extension, role/policy và dependencies đặc thù Supabase trước migration; không import mù toàn bộ managed schema sang RDS.

**Dọn lab:** xóa DB thử, kiểm tra snapshot/backup còn giữ và chi phí lưu trữ của chúng.

<a id="secrets"></a>

## 6. AWS Secrets Manager — thông tin bí mật

### Lý thuyết cần nắm

Học secret, ARN, version, quyền đọc, mã hóa, rotation và cache. Dịch vụ quản lý giá trị bí mật, nhưng ứng dụng phải có cơ chế lấy và sử dụng chúng. Tạo một secret không tự sửa application.properties. Đọc [Secrets Manager là gì](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html).

Ứng dụng dùng IAM Role để đọc secret cần thiết; credential AWS của EC2 nên đến từ role. Mật khẩu DB và key Merriam-Webster là các ứng viên phù hợp. Xem [tạo secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/create_secret.html) và [đọc secret bằng Java SDK](https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets-java-sdk.html).

### Cách đưa vào sơ đồ

Vẽ EC2 → Secrets Manager, nhãn “GetSecretValue khi nạp cấu hình/cache”. Đây không phải đường đi của từng request lấy dữ liệu học tập.

### Lab 06 — ứng dụng đọc cấu hình đúng quyền

1. Tạo secret lab chứa username/password giả hoặc key mẫu.
2. Cấp role EC2 quyền đọc đúng secret.
3. Viết chương trình lab nhỏ đọc secret bằng SDK; chỉ báo trạng thái thành công, không in giá trị ra log.
4. Thử role không có quyền: thao tác bị từ chối.
5. Thay giá trị secret và kiểm tra cách ứng dụng nạp lại sau restart hoặc hết thời gian cache.
6. Bài nâng cao: nghiên cứu rotation cho DB và cách connection pool nhận credential mới.

**Đạt khi:** source không chứa secret thật; ứng dụng đọc được nhưng danh tính không có quyền không đọc được.

**Tự hỏi:** nếu đổi mật khẩu trong secret nhưng DB vẫn giữ mật khẩu cũ thì điều gì xảy ra? Phải phối hợp cập nhật cả hai; không coi việc sửa chuỗi secret là đã rotation DB.

**Dọn lab:** lên lịch xóa secret lab khi không dùng; ghi lại secret nào cần giữ cho bài tổng hợp.

<a id="cloudwatch"></a>

## 7. Amazon CloudWatch — theo dõi và tìm lỗi

### Lý thuyết cần nắm

Phân biệt metric, log, alarm và dashboard. Học log group, log stream, retention, thống kê theo khoảng thời gian và ngưỡng cảnh báo. Xem [CloudWatch là gì](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/WhatIsCloudWatch.html).

EC2 không mặc định gửi toàn bộ log Spring Boot hay RAM trong máy lên CloudWatch. Cần agent hoặc cơ chế xuất telemetry tương ứng. Đọc [CloudWatch Agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Install-CloudWatch-Agent.html).

Áp dụng: tìm lỗi Cognito đồng bộ status, lỗi S3/Polly, timeout DB, tăng HTTP 5xx. Audit thay đổi cấu hình nghiệp vụ trong PostgreSQL vẫn có vai trò riêng.

### Cách đưa vào sơ đồ

Vẽ đường quan sát riêng từ EC2/RDS/ALB đến CloudWatch, ghi “logs/metrics”; không đặt CloudWatch ở giữa đường request người dùng.

### Lab 07 — tìm được lỗi có chủ đích

1. Cài agent, cấp quyền gửi log/metric phù hợp.
2. Chỉ định file log Spring Boot và log group lab; đặt retention, ví dụ 7 ngày.
3. Gửi một request lab gây validation error và một lỗi upstream giả lập có kiểm soát.
4. Tìm đúng sự kiện theo thời gian và request ID nếu ứng dụng có.
5. Tạo dashboard CPU/RAM, DB connections và lỗi ALB khi các thành phần đã tồn tại.
6. Tạo alarm CPU với ngưỡng lab; kiểm tra chuyển trạng thái rồi trả cấu hình bình thường.

Ví dụ [truy vấn Logs Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax.html) cho log dạng text:

~~~text
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 50
~~~

**Đạt khi:** chỉ ra được lỗi xuất phát từ app, mạng hay AWS; log không có token/mật khẩu. Alarm chỉ gửi thông báo ra ngoài khi bạn cấu hình thêm kênh nhận thích hợp.

**Dọn lab:** xóa alarm/dashboard không cần, dừng agent nếu bỏ EC2 và xử lý log group theo retention mong muốn.

<a id="cognito"></a>

## 8. Amazon Cognito — tài khoản và đăng nhập

### Lý thuyết cần nắm

User Pool quản lý tài khoản/xác thực và cấp JWT. Identity Pool cấp credential AWS tạm thời cho client khi cần; hai phần phục vụ mục tiêu khác nhau. Đọc [Cognito là gì](https://docs.aws.amazon.com/cognito/latest/developerguide/what-is-amazon-cognito.html).

Học app client, sub, access token, ID token, refresh token, challenge, xác nhận email, issuer và JWKS. Backend phải xác minh chữ ký/expiry/issuer, đúng app client và loại token; decode JWT chỉ đọc nội dung. Xem [xác minh JWT](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html).

Áp dụng: Cognito xác thực; users local lưu role learner/admin, status, liên kết bằng sub. Các lệnh AdminDisableUser/AdminEnableUser cần quyền IAM ở Backend.

### Cách đưa vào sơ đồ

Vẽ Browser → API Backend → Cognito cho luồng login hiện tại của dự án. Token quay lại browser, rồi browser gửi Bearer token tới API. Thêm liên kết Backend ↔ users trong DB.

### Lab 08 — đăng nhập và phân quyền đúng

1. Tạo User Pool/app client lab theo [hướng dẫn bắt đầu Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/getting-started-user-pools-application.html).
2. Dùng email thử nghiệm của bạn, đăng ký và xác nhận tài khoản.
3. Đăng nhập, gọi API bảo vệ, refresh rồi gọi lại.
4. Thử thiếu token, token hết hạn, sai pool/client, ID token thay access token.
5. Thử learner gọi Admin API; tài khoản local BLOCKED gọi API.
6. Thử khóa/mở tài khoản lab qua cả Cognito và trạng thái local.

**Đạt khi:** phân biệt 401 — xác thực không hợp lệ và 403 — đã xác thực nhưng không được phép.

**Lưu ý:** Cognito thu hồi token không tự làm thư viện JWT chỉ kiểm tra chữ ký/expiry biết được trạng thái thu hồi. Giữ cơ chế kiểm tra local BLOCKED; xem [token revocation](https://docs.aws.amazon.com/cognito/latest/developerguide/token-revocation.html).

**Dọn lab:** xóa tài khoản/app client/pool thử nghiệm riêng; không dùng pool dự án để luyện thao tác xóa.

<a id="polly"></a>

## 9. Amazon Polly — phát âm tiếng Anh

### Lý thuyết cần nắm

Polly chuyển text thành giọng nói. Học language, voice, engine, output format, sample rate và SSML cơ bản. Engine/voice phải tương thích và khả dụng ở Region sử dụng. Đọc [Polly là gì](https://docs.aws.amazon.com/polly/latest/dg/what-is.html), [các voice được hỗ trợ](https://docs.aws.amazon.com/polly/latest/dg/available-voices.html).

Áp dụng hiện tại: Backend tạo MP3 tiếng Anh, lưu S3, ghi key vào DB. Khi người học bấm icon, trình duyệt tải/phát file; không cần gọi Polly lại mỗi lần nghe.

### Cách đưa vào sơ đồ

Vẽ riêng hai flow: tạo file “Admin → Backend → Polly → Backend → S3”; nghe file “Learner → Backend lấy audioUrl → S3”.

### Lab 09 — từ chữ thành MP3

1. Theo [hướng dẫn bắt đầu Polly](https://docs.aws.amazon.com/polly/latest/dg/getting-started.html), chọn voice tiếng Anh, ví dụ Joanna.
2. Tổng hợp từ “sugar”, chọn engine hỗ trợ voice đó và định dạng MP3.
3. Nghe thử rồi so sánh với text đầu vào; dùng từ tiếng Anh, không dùng nghĩa tiếng Việt.
4. Upload MP3 vào prefix lab S3.
5. Tạo URL có hạn và phát bằng trình duyệt.
6. Thử engine/voice không tương thích và role thiếu quyền trong lab; ghi cách đọc lỗi.
7. Mở lại file có sẵn để chứng minh thao tác nghe không cần tổng hợp lại.

**Đạt khi:** giải thích được điểm nào quyết định ngôn ngữ phát âm, ai sinh MP3, ai lưu file và ai phát audio.

**Lưu ý dự án:** âm thanh có đúng từ còn phụ thuộc key gắn đúng vocabulary và FE dùng đúng audioUrl. Giọng đọc tiếng Anh không tự sửa dữ liệu liên kết sai.

**Dọn lab:** xóa file thử không cần; Polly không có máy chủ riêng phải giữ chạy để phát lại MP3 đã lưu.

<a id="acm"></a>

## 10. AWS Certificate Manager — chứng chỉ HTTPS

### Lý thuyết cần nắm

Học TLS, certificate, domain/SAN, DNS validation, trạng thái Issued, gia hạn và nơi gắn chứng chỉ. ACM quản lý chứng chỉ; domain/DNS và listener vẫn cần cấu hình riêng. Đọc [ACM là gì](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html).

Với kiến trúc này: chứng chỉ cho ALB nằm cùng Region với ALB; chứng chỉ cho custom domain CloudFront phải ở **us-east-1**. Xem [tích hợp ACM](https://docs.aws.amazon.com/acm/latest/userguide/acm-services.html).

Áp dụng: app.example.com cho FE, api.example.com cho Backend; đây là tên minh họa, phải thay bằng domain bạn kiểm soát.

### Cách đưa vào sơ đồ

Gắn nhãn ACM vào CloudFront/ALB bằng đường nét đứt. Chứng chỉ bảo vệ kết nối tại nơi kết thúc TLS; nó không chạy code Backend hay sửa CORS.

### Lab 10 — cấp chứng chỉ đúng domain

**Chuẩn bị:** quyền sửa DNS của một domain/subdomain thử nghiệm. Không cần mua domain mới nếu nhóm đã cấp subdomain.

1. Đọc [cách yêu cầu public certificate](https://docs.aws.amazon.com/acm/latest/userguide/acm-public-certificates.html).
2. Request chứng chỉ API ở Region chính, chọn DNS validation.
3. Thêm CNAME validation ở nhà cung cấp DNS, đợi Issued.
4. Gắn vào HTTPS listener khi làm Bài 11.
5. Nếu FE dùng custom domain, request chứng chỉ tương ứng tại us-east-1 cho Bài 12.
6. Kiểm tra hostname, chuỗi chứng chỉ và thời hạn trong trình duyệt.

**Đạt khi:** truy cập đúng domain không báo lỗi TLS; biết vì sao không dùng chứng chỉ cho domain khác.

**Nếu chưa có domain:** học lý thuyết trước. Lab CloudFront vẫn dùng được HTTPS qua domain mặc định của CloudFront; chưa thể luyện đầy đủ ACM public certificate cho domain tự chọn.

**Dọn lab:** gỡ chứng chỉ khỏi tài nguyên trước khi xóa; kiểm tra các DNS record còn cần cho domain dùng chung.

<a id="alb"></a>

## 11. Elastic Load Balancing / ALB — cửa vào API

### Lý thuyết cần nắm

ALB xử lý HTTP/HTTPS ở tầng ứng dụng. Học internet-facing/internal, listener, rule, target group, health check và connection draining. Đọc [ALB là gì](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/introduction.html).

ALB thông thường dùng subnet ở ít nhất hai AZ. Lab có thể bắt đầu với một target để hiểu routing nhưng chưa có dự phòng Backend; lab hai target mới giúp quan sát lỗi một máy. Xem [tạo ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-application-load-balancer.html).

Áp dụng: browser gọi api.example.com:443 → ALB → Spring Boot:8080. Trong lab có thể kết thúc TLS ở ALB rồi dùng HTTP nội bộ; nếu cần mã hóa tới Backend, phải cấu hình thêm HTTPS target.

### Cách đưa vào sơ đồ

Vẽ ALB gắn public subnet của hai AZ; EC2 ở private subnet. Ghi rõ 443 bên ngoài và 8080 bên trong; ACM gắn listener, Security Group Backend chỉ nhận port app từ SG của ALB.

### Lab 11 — routing và health check

1. Tạo hai dịch vụ HTTP lab trên hai EC2, trả về mã nhận diện A/B.
2. Thêm GET /health trả 200 **trong ứng dụng lab**; đây không phải khẳng định Backend hiện tại đã có route đó.
3. Tạo target group và ALB, đăng ký target, kiểm tra healthy.
4. Gắn listener HTTPS với ACM và DNS API nếu đã có domain.
5. Gửi nhiều request mới, quan sát các target phục vụ.
6. Dừng một dịch vụ lab, quan sát health check và máy còn lại.
7. Thử health path sai/401 rồi sửa lại; không coi việc mở matcher chấp nhận 401 là đã có health endpoint đúng.

**Đạt khi:** chỉ ra listener, target group, health path, source SG và lý do target unhealthy. Xem [health check và reason code](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html).

**Lưu ý:** nếu tất cả target đều unhealthy, ALB có thể fail-open tới chúng. Health check không thay thế xác thực hoặc firewall. ALB cũng không tự tạo EC2 mới.

**Dọn lab:** xóa ALB, listener/target group và máy lab không dùng; stop EC2 không tự xóa ALB.

<a id="cloudfront"></a>

## 12. Amazon CloudFront — phân phối Frontend

### Lý thuyết cần nắm

CloudFront là CDN. Học distribution, origin, edge location, cache behavior, TTL, Cache-Control, invalidation và viewer/origin protocol. Đọc [CloudFront là gì](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html).

Frontend React build thành file tĩnh trên S3. CloudFront lấy file từ bucket qua OAC; dùng S3 bucket origin thông thường, không dùng S3 website endpoint nếu muốn OAC. Xem [giới hạn truy cập S3 bằng OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html).

### Cách đưa vào sơ đồ

Vẽ browser → CloudFront → S3 FE, nhãn HTTPS/OAC. Browser gọi API qua đường riêng tới ALB; không mặc định API cá nhân được cache chung.

### Lab 12 — đưa React lên web

1. Làm [lab tạo distribution với S3](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/GettingStarted.SimpleDistribution.html) bằng trang mẫu trước.
2. Dùng bucket FE riêng tư và OAC/bucket policy phù hợp.
3. Mở domain CloudFront qua HTTPS; kiểm tra direct S3 URL không ký không đọc được.
4. Build React cho môi trường lab, đặt API base URL HTTPS và upload nội dung bản build.
5. Cấu hình default root object index.html; kiểm tra mở trực tiếp route con khi refresh.
6. Chọn cách xử lý SPA fallback; kiểm tra asset không tồn tại không bị che thành một response HTML “thành công”.
7. Triển khai bản FE mới, quan sát cache; dùng file có hash và chính sách cache phù hợp.
8. Kiểm tra CORS giữa domain FE và API, preflight OPTIONS và audio.

**Đạt khi:** tải được FE, refresh route con được, API/âm thanh hoạt động và bucket FE không cần public.

**HTTPS:** domain CloudFront mặc định đã có chứng chỉ; custom domain cần chứng chỉ phù hợp. Đọc [HTTPS tới CloudFront](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-https-viewers-to-cloudfront.html). Web HTTPS gọi API HTTP có thể bị chặn mixed content.

**Dọn lab:** disable rồi xóa distribution khi bỏ lab; kiểm tra OAC/bucket/chứng chỉ nào còn dùng chung.

<a id="ve-so-do"></a>

## Cách vẽ architecture

### Công cụ và tài liệu

- [Mở draw.io / diagrams.net](https://app.diagrams.net/).
- [Hướng dẫn vẽ AWS bằng draw.io](https://www.drawio.com/docs/diagram-types/aws-diagrams/): bật thư viện AWS và chọn template.
- [Bộ icon AWS chính thức](https://aws.amazon.com/architecture/icons/): dùng icon và nhóm tài nguyên phù hợp.
- [AWS Well-Architected Framework](https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html): dùng để giải thích các lựa chọn về vận hành, bảo mật, độ tin cậy, hiệu năng, chi phí và tính bền vững.

Các sơ đồ dưới đây dùng Mermaid; có thể xem bằng trình đọc hỗ trợ Mermaid. Khi nộp bài lab, vẽ lại bằng icon AWS và xuất PNG/PDF, giữ cả file .drawio để chỉnh sửa.

### Sơ đồ 1 — chức năng từng dịch vụ

**Kiến trúc mục tiêu**, chưa phải xác nhận các tài nguyên đã tồn tại trên AWS.

~~~mermaid
flowchart LR
    User["Người học / Admin"]
    CF["12. CloudFront"]
    FE["4. S3 - bản build React"]
    ALB["11. ALB"]
    BE["3. EC2 - Spring Boot"]
    DB[("5. RDS PostgreSQL")]
    Media["4. S3 - ảnh / MP3"]
    Cog["8. Cognito"]
    Polly["9. Polly"]
    Secret["6. Secrets Manager"]
    CW["7. CloudWatch"]
    IAM["1. IAM Role"]
    ACM["10. ACM"]
    Network["2. VPC - chứa ALB, EC2, RDS"]
    User -->|"HTTPS tải FE"| CF
    CF -->|"OAC"| FE
    User -->|"HTTPS gọi API + Bearer"| ALB
    ALB -->|"HTTP 8080 trong lab"| BE
    BE -->|"PostgreSQL TLS 5432"| DB
    BE -->|"Auth / quản trị tài khoản"| Cog
    BE -->|"Sinh MP3"| Polly
    BE -->|"Upload / ký URL"| Media
    User -->|"Tải file bằng URL đã ký"| Media
    BE -->|"Đọc cấu hình bí mật"| Secret
    BE -.->|"Log / metric"| CW
    IAM -.->|"Cấp quyền"| BE
    ACM -.->|"Cert FE ở us-east-1"| CF
    ACM -.->|"Cert API cùng Region ALB"| ALB
    Network -.->|"Ranh giới mạng; xem sơ đồ 2"| BE
~~~

Đường liền biểu diễn request/dữ liệu; đường nét đứt biểu diễn quyền, chứng chỉ hoặc quan hệ hỗ trợ. ACM có thể cần hai tài nguyên chứng chỉ ở hai Region, dù vẫn là một dịch vụ trong danh sách học.

### Sơ đồ 2 — cách đặt tài nguyên vào mạng

Mẫu học: ALB dùng hai public subnet, một EC2 ứng dụng và một RDS Single-AZ; DB subnet group có subnet ở hai AZ. Mẫu này **chưa có dự phòng cho EC2 và DB**.

~~~mermaid
flowchart TB
    Browser["Browser"]
    External["API công khai Cognito / Polly / Merriam-Webster"]
    subgraph Region["Region chính"]
        Services["S3 / Secrets Manager / CloudWatch - ngoài VPC"]
        subgraph VPC["VPC 10.20.0.0/16"]
            IGW["Internet Gateway"]
            subgraph Public["Public subnet AZ-A và AZ-B"]
                ALB["Internet-facing ALB - gắn cả hai subnet"]
                NAT["NAT Gateway - một AZ cho lab nếu cần"]
            end
            subgraph App["Private app subnet AZ-A"]
                EC2["EC2 Spring Boot :8080"]
            end
            subgraph Data["DB subnet group - private AZ-A và AZ-B"]
                RDS[("RDS Single-AZ :5432")]
            end
            ALB -->|"SG-ALB → SG-BE"| EC2
            EC2 -->|"SG-BE → SG-DB; TLS"| RDS
            EC2 -->|"Default route outbound"| NAT
            NAT --> IGW
        end
    end
    Browser -->|"HTTPS qua IGW"| ALB
    IGW -->|"HTTPS outbound"| External
    IGW -->|"Public endpoint trong mẫu này"| Services
~~~

Sơ đồ này rút gọn AZ/subnet để dễ đọc. Khi vẽ lại, tạo **hai khung AZ riêng** và đặt đúng từng subnet. NAT ở public subnet phải có cấu hình IP/route phù hợp; RDS subnet không cần default route ra NAT chỉ để nhận kết nối DB từ EC2.

Mẫu nâng cao: thêm app instance ở AZ-B, cơ chế Auto Scaling và Multi-AZ DB theo nhu cầu. NAT mỗi AZ hoặc các endpoint cần được thiết kế theo khả dụng/chi phí; một NAT trong lab không tạo khả dụng cao. Nguồn mẫu mạng: [AWS VPC với ALB, private subnet và NAT](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-example-private-subnets-nat.html).

### Bảng mạng phải giải thích được trên sơ đồ

| Thành phần | Inbound chính trong mẫu | Outbound cần xét |
|---|---|---|
| ALB / SG-ALB | HTTPS 443 từ người dùng; HTTP 80 chỉ khi dùng chuyển hướng | Port ứng dụng/health check tới SG-BE |
| EC2 / SG-BE | 8080 từ SG-ALB; kênh quản trị được giới hạn riêng | 5432 tới DB, HTTPS tới AWS/provider và các phụ thuộc vận hành |
| RDS / SG-DB | 5432 từ SG-BE | Theo kết nối DB hợp lệ và nhu cầu quản trị cụ thể |
| Private app route | Route nội bộ VPC | 0.0.0.0/0 → NAT nếu dùng public IPv4 endpoints |
| Public subnet route | Route nội bộ VPC | 0.0.0.0/0 → Internet Gateway |

IAM và Security Group xử lý hai lớp khác nhau. Có đường mạng chưa có nghĩa có quyền gọi AWS; có quyền IAM chưa có nghĩa có đường mạng.

### Sơ đồ 3 — tạo audio và nghe audio

~~~mermaid
sequenceDiagram
    actor Admin
    participant BE as Spring Boot trên EC2
    participant P as Amazon Polly
    participant S as Amazon S3
    participant DB as PostgreSQL
    actor Learner
    Admin->>BE: Tạo từ / generate-missing-audio
    BE->>P: Text tiếng Anh + voice + engine
    P-->>BE: Dữ liệu MP3
    BE->>S: Upload object key mới
    BE->>DB: Lưu key liên kết vocabulary
    Learner->>BE: Lấy learning hoặc game questions
    BE->>DB: Đọc vocabulary / snapshot
    BE-->>Learner: audioUrl có hạn
    Learner->>S: Bấm icon, tải MP3 qua URL đã ký
    S-->>Learner: Audio
~~~

**Đây không phải một transaction phân tán tự động.** Nếu upload S3 thành công nhưng lưu DB thất bại, cần xử lý retry/dọn object; nếu DB có key nhưng file bị xóa, FE vẫn có thể phát lỗi audio.

### Quy trình vẽ lại bằng draw.io

1. Viết yêu cầu: “React FE, Spring Boot API, PostgreSQL, login, audio”.
2. Chọn sơ đồ chức năng hay deployment; đặt tên môi trường Lab/Production.
3. Mở draw.io → More Shapes → bật thư viện AWS phù hợp.
4. Tạo khung AWS Cloud, Region, VPC; chia AZ và subnet trước khi đặt máy chủ.
5. Đặt CloudFront/IAM theo phạm vi phù hợp; S3/Cognito/Polly ở ngoài VPC. ACM cho CloudFront ghi rõ us-east-1.
6. Đặt ALB, EC2, RDS vào đúng subnet; thêm đường outbound để không bỏ quên các cuộc gọi provider.
7. Nối luồng FE, API, DB và media; ghi protocol/port ở các đường quan trọng.
8. Thêm đường nét đứt cho quyền IAM, cert và monitoring; tạo legend.
9. Kiểm tra từng tài nguyên có mục đích và đường đi của một request hoàn chỉnh.
10. Xuất hình, giữ file nguồn; ghi ngày và phiên bản sơ đồ.

Tự thuyết trình trong 5 phút: request tới đâu, xác thực ở đâu, dữ liệu nằm đâu, tài nguyên nào public, lỗi một máy thì sao, chi phí nào chạy liên tục?

<a id="lab-tong-hop"></a>

## Lab tổng hợp Alphabet Universe

### Giai đoạn A — nền tảng và Backend

**Dịch vụ:** IAM, VPC, EC2.

1. Vẽ sơ đồ và bảng route/SG trước.
2. Chạy HTTP mẫu trên EC2 để tách lỗi hạ tầng khỏi lỗi ứng dụng.
3. Gắn role, kiểm tra credential tạm thời hoạt động.
4. Triển khai Backend với cấu hình lab riêng.

**Bằng chứng:** sơ đồ mạng, trạng thái tiến trình, request mẫu và lỗi bị chặn đúng.

### Giai đoạn B — database và secrets

**Dịch vụ:** RDS, Secrets Manager.

1. Tạo RDS lab private và kết nối TLS từ EC2.
2. Import bản sao dữ liệu phù hợp; lập danh mục bảng, constraints, identity/sequence, index, function/trigger.
3. Tích hợp cơ chế đọc secret vào cấu hình lab; việc này cần code/config triển khai riêng.
4. Test Auth Sync, ghi attempt, progress, weekly_scores và game config audit trên DB lab.
5. Restore snapshot sang DB mới và đối chiếu.

**Bằng chứng:** kết nối TLS, dữ liệu sau ghi/restore, lần INSERT sau migration không lỗi ID, audit hoạt động.

### Giai đoạn C — tài khoản và audio

**Dịch vụ:** Cognito, Polly, S3.

1. Dùng User Pool và dữ liệu lab; kiểm tra login, refresh, Sync.
2. Kiểm tra sai mật khẩu/token, learner truy cập Admin và tài khoản BLOCKED.
3. Tạo một vocabulary tiếng Anh, sinh MP3, xác nhận key DB đúng file S3.
4. Test learning, tạo phiên CHECKPOINT/FREE_PLAY/REVIEW phù hợp điều kiện nghiệp vụ và resume.
5. Bấm icon nghe đúng từ; thử URL hết hạn và file thiếu.

**Bằng chứng:** mã HTTP mong đợi, trạng thái DB/Cognito, MP3 và request tải file đã che thông tin nhạy cảm.

### Giai đoạn D — HTTPS và giao diện

**Dịch vụ:** ACM, ALB, CloudFront, S3.

1. Chuẩn bị domain/subdomain và cert; nếu chưa có domain thì làm CloudFront mặc định trước.
2. Cấu hình ALB, health check và Backend SG.
3. Đưa React lên S3 riêng tư + CloudFront OAC.
4. Đặt API base URL HTTPS; cập nhật origin CORS và các URL callback/logout Cognito nếu flow sử dụng chúng.
5. Test refresh route FE, login, gọi API và nghe audio từ trình duyệt thực.

**Bằng chứng:** sơ đồ deployment, domain HTTPS, healthy target, request CORS và file frontend được tải.

### Giai đoạn E — vận hành

**Dịch vụ:** CloudWatch, phối hợp toàn bộ hệ thống.

1. Thu log và metric cần thiết; đặt retention.
2. Thực hiện lỗi có chủ đích trong lab: sai key S3, thiếu một quyền role, sai health path, chặn đường DB.
3. Xác định nguyên nhân từ bằng chứng, khôi phục cấu hình và kiểm tra lại.
4. Xuất danh sách tài nguyên, ước tính chi phí, tài nguyên giữ/xóa.

**Điều kiện hoàn thành lab tổng hợp:**

- [ ] Tự giải thích đủ 12 thành phần trên sơ đồ.
- [ ] FE dùng HTTPS, gọi được Backend và refresh route.
- [ ] API bảo vệ vẫn kiểm tra JWT, role và local status.
- [ ] Backend dùng role; secret không nằm trong repository/bản build FE.
- [ ] DB private, dữ liệu có thể ghi/đọc và phục hồi.
- [ ] Audio đúng vocabulary, URL có cơ chế làm mới khi cần.
- [ ] Có log tìm lỗi và metric kiểm tra tài nguyên.
- [ ] Nêu rõ chỗ chưa có khả dụng cao, không gọi lab một máy là production HA.
- [ ] Dọn tài nguyên và kiểm tra chi phí sau buổi học.

<a id="lich-hoc"></a>

## Lịch học và tiêu chí hoàn thành

Lịch đề xuất **6 tuần**, khoảng **7–10 giờ/tuần**; đây là ước lượng học cá nhân, không phải thời lượng khóa AWS.

| Tuần | Nội dung | Sản phẩm phải có |
|---|---|---|
| 1 | IAM, VPC | Sơ đồ mạng, bảng SG/route, giải thích trust/permission |
| 2 | EC2, S3 | HTTP/Backend mẫu chạy lại được, object private và presigned URL |
| 3 | RDS, Secrets Manager | DB lab, thử restore, đọc secret đúng quyền |
| 4 | CloudWatch, Cognito | Tìm được log lỗi, test auth sai và đúng |
| 5 | Polly, ACM, ALB | MP3 tiếng Anh, hiểu chứng chỉ, routing/health check |
| 6 | CloudFront và lab tổng hợp | FE HTTPS, kiến trúc hoàn chỉnh, báo cáo test và chi phí |

Trong từng buổi: đọc tài liệu → ghi 5 ý lý thuyết → vẽ → làm lab → kiểm tra lỗi → lưu kết quả và dọn tài nguyên. Không cần học thuộc toàn bộ nút trên Console; phải hiểu tài nguyên, quyền và đường đi dữ liệu.

### Mẫu ghi chép cho từng dịch vụ

~~~text
Tên dịch vụ:
Vấn đề nó giải quyết:
5 khái niệm chính:
Đầu vào / đầu ra:
Region và phạm vi mạng:
Quyền IAM cần:
Luồng trên architecture:
Các bước lab mình đã làm:
Kết quả mong đợi / thực tế:
Một lỗi mình đã tạo và cách xử lý:
Chi phí cần theo dõi:
Tài nguyên đã dọn / còn giữ:
Link nguồn đã đọc:
~~~

### Bộ câu hỏi tự kiểm tra

| Dịch vụ | Câu hỏi phải tự trả lời được |
|---|---|
| IAM | Trust policy khác permission policy thế nào? SDK trên EC2 nhận credential từ đâu? |
| VPC | Public/private subnet khác ở route nào? Backend private gọi Merriam-Webster qua đâu? |
| EC2 | Ứng dụng chạy ở port nào? Reboot có tự lên? Khi máy chết ai tạo máy thay thế? |
| S3 | Key khác URL thế nào? Ai cấp quyền đọc MP3? CORS có làm bucket public không? |
| RDS | Vì sao hai DB subnet không đồng nghĩa Multi-AZ? Restore có giữ được identity/trigger không? |
| Secrets Manager | Ứng dụng đọc lúc nào? Cache khi nào hết? Đổi secret đã đổi DB password chưa? |
| CloudWatch | Log, metric, alarm khác nhau thế nào? RAM/log app có tự xuất hiện không? |
| Cognito | sub để làm gì? Access/ID/refresh token khác nhau thế nào? Ai kiểm tra local BLOCKED? |
| Polly | Voice/engine/text ảnh hưởng gì? Mỗi lần nghe có cần gọi tổng hợp lại không? |
| ACM | Ai xác minh quyền domain? Cert CloudFront và ALB đặt Region nào? |
| ALB | Listener → rule → target group → target đi thế nào? Vì sao health check 401 là sai? |
| CloudFront | Cache hit/miss là gì? OAC bảo vệ đường nào? FE mới triển khai có thể thấy bản cũ vì sao? |

### Thang tự đánh giá

- **Mức 1 — nhớ tên:** mô tả công dụng.
- **Mức 2 — hiểu:** giải thích được khái niệm và ranh giới trách nhiệm.
- **Mức 3 — thực hành:** tự làm lại lab, đo được kết quả.
- **Mức 4 — áp dụng:** ghép vào Alphabet Universe, biết tìm lỗi và đánh đổi chi phí/khả dụng.

Mục tiêu trước khi triển khai dự án thật: mỗi dịch vụ đạt ít nhất mức 3; với IAM/VPC/Cognito/RDS, phải giải thích được cả trường hợp lỗi.

<a id="tra-loi"></a>

## Tra lỗi thường gặp

| Hiện tượng trong lab | Kiểm tra theo thứ tự |
|---|---|
| EC2 không ra Internet | DNS → route → public IPv4/IGW hoặc private NAT → SG/NACL → firewall/proxy |
| AWS trả AccessDenied | Danh tính/role thực tế → action → resource ARN → trust/resource policy → explicit deny |
| Backend không vào DB | Endpoint/port → DNS/route → SG → tiến trình/DB status → TLS → credential |
| ALB target unhealthy | Reason code → port app → SG health check → path → mã trả về → timeout |
| API trả 401 | Token có gửi không → có hết hạn không → chữ ký/issuer → token_use/client_id → user local theo contract |
| API trả 403 | Quyền learner/admin và status local; phân biệt với 403 từ S3/CloudFront |
| Audio không phát | audioUrl đúng vocabulary → S3 key/file → URL/credential hết hạn → Content-Type → Network/Console trình duyệt |
| Polly đọc sai ngôn ngữ/từ | Text là word hay meaning → voice/language → engine → file cũ/key liên kết |
| FE HTTPS gọi API lỗi | API có HTTPS không → TLS/domain → CORS/preflight → token; xem lỗi thật trong Network |
| CloudFront trả bản FE cũ | File build/đường upload → origin → Cache-Control/TTL → invalidation → cache trình duyệt |
| Không thấy log app | File log có tồn tại → agent đang chạy → quyền gửi log → Region/log group → khoảng thời gian |
| Đổi secret mà app vẫn dùng giá trị cũ | Ứng dụng có đọc secret không → version/cache → refresh/restart → connection pool |

Đây là hướng điều tra, không phải kết luận nguyên nhân chỉ từ một HTTP status. Luôn ghi lại thời điểm, request và nguồn phát lỗi.

<a id="don-tai-nguyen"></a>

## Dọn tài nguyên sau lab

Chỉ dọn tài nguyên có tag/tên lab đã xác nhận; không áp dụng danh sách này để xóa tài nguyên dự án đang dùng.

1. Lưu sơ đồ, cấu hình mẫu đã che bí mật và bằng chứng cần nộp.
2. Disable/xóa CloudFront nếu bỏ lab; xem tài nguyên nào đang dùng bucket/chứng chỉ.
3. Xóa ALB, target group không dùng.
4. Terminate EC2 lab; kiểm tra EBS, snapshot, Elastic IP và public IPv4 còn cấp.
5. Xóa DB lab theo chính sách giữ dữ liệu; kiểm tra snapshot và retained backup còn phí.
6. Xóa NAT Gateway rồi xử lý địa chỉ IP liên quan; không chỉ xóa EC2.
7. Dọn S3 object/version/delete marker/multipart upload thử; xóa bucket khi hết phụ thuộc.
8. Xóa alarm/log group không cần hoặc đặt retention.
9. Gỡ role/policy, app client/User Pool, secret và certificate chỉ thuộc lab.
10. Xóa SG/subnet/route/gateway/VPC sau cùng khi không còn phụ thuộc; kiểm tra Billing sau đó vì dữ liệu chi phí có độ trễ.

Các bài lab có thể dùng lại tài nguyên qua nhiều buổi, nhưng phải ghi rõ tài nguyên nào đang giữ và vì sao. Không coi “stop máy” là đã dừng toàn bộ chi phí của kiến trúc.

## Ghi chú nguồn và phạm vi

- Link tài liệu đã được tra cứu khi biên soạn ngày 05/11/2026; Console, tên mục và khóa học có thể đổi.
- AWS Docs là nguồn chính cho lý thuyết/cấu hình; các bước lab, lịch học và ví dụ architecture là phần đề xuất áp dụng cho Alphabet Universe.
- Các link học đã đặt tại từng chương để đọc ngay theo chủ đề. Nếu khóa Skill Builder yêu cầu đăng nhập hoặc không mở được, dùng AWS Docs của chương và tìm tên khóa từ cổng Skill Builder.
- Tài liệu là hướng dẫn học, không phải xác nhận đã provision AWS, đổi database hoặc kiểm thử live toàn bộ hệ thống.
