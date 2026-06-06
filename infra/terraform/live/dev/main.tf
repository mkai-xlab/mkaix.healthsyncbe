module "network" {
  source      = "../../modules/network"
  vpc_cidr    = var.vpc_cidr
  environment = "dev"
}


module "compute" {
  source        = "../../modules/compute"
  ami_id        = data.aws_ami.ubuntu.id
  instance_type = "m7i-flex.large"
  subnet_ids    = module.network.public_subnet_ids
  key_path      = "~/.ssh/server.pub"
  vpc_id        = module.network.vpc_id
  vpc_cidr      = var.vpc_cidr
  environment   = "dev"
  user_data     = templatefile("${path.module}/user_data.sh.tftpl", {
    nginx_config = file("${path.module}/nginx.conf")
  })
}
