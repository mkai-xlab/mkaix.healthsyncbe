resource "aws_instance" "server" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_ids[0]
  key_name               = aws_key_pair.server_key.key_name
  vpc_security_group_ids = [aws_security_group.server_sg.id]
  monitoring             = true
  source_dest_check      = false
  iam_instance_profile   = aws_iam_instance_profile.instance_profile.name
  user_data              = var.user_data
  tags = {
    Name        = "${var.environment}-server"
    Environment = var.environment
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_size           = 10
    volume_type           = "gp3"
    delete_on_termination = true
  }

  ebs_block_device {
    volume_size = 10
    volume_type = "gp3"
    device_name = "/dev/sdb"
  }
}

resource "aws_eip" "server_ip" {
  instance = aws_instance.server.id
  tags = {
    Name        = "${var.environment}-server-eip"
    Environment = var.environment
  }
}

resource "aws_eip_association" "server_ip_assoc" {
  instance_id   = aws_instance.server.id
  allocation_id = aws_eip.server_ip.id
}

resource "aws_key_pair" "server_key" {
  key_name   = "${var.environment}-server-key"
  public_key = file(var.key_path)
  tags = {
    Environment = var.environment
  }
}

resource "aws_security_group" "server_sg" {
  name        = "${var.environment}-server-sg"
  description = "Allow SSH and HTTP for ${var.environment}"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 22
    to_port         = 22
    protocol        = "tcp"
    security_groups = [aws_security_group.eice_sg.id]
    # cidr_blocks = [var.vpc_cidr]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "${var.environment}-server-sg"
    Environment = var.environment
  }
}


resource "aws_security_group" "eice_sg" {
  name        = "${var.environment}-eice-sg"
  description = "Allow EC2 Instance Connect for ${var.environment}"
  vpc_id      = var.vpc_id
  egress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name        = "${var.environment}-eice-sg"
    Environment = var.environment
  }
}

resource "aws_ec2_instance_connect_endpoint" "eice" {
  subnet_id          = var.subnet_ids[1]
  security_group_ids = [aws_security_group.eice_sg.id]
  tags = {
    Name        = "${var.environment}-eice"
    Environment = var.environment
  }
}

resource "aws_iam_instance_profile" "instance_profile" {
  name = "${var.environment}-instance-profile"
  role = aws_iam_role.ec2_ssm_role.name
  tags = {
    Name        = "${var.environment}-instance-profile"
    Environment = var.environment
  }
}

resource "aws_iam_role" "ec2_ssm_role" {
  name = "${var.environment}-ec2-ssm-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.environment}-ec2-ssm-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2_ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
