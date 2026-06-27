module "network" {
  source      = "../../modules/network"
  vpc_cidr    = var.vpc_cidr
  environment = "dev"
}

module "compute" {
  source        = "../../modules/compute"
  ami_id        = "ami-0dbf91f0aff3d5d2f"
  instance_type = "m7i-flex.large"
  subnet_ids    = module.network.public_subnet_ids
  key_path      = "~/.ssh/heathsync-dev-server.pub"
  vpc_id        = module.network.vpc_id
  vpc_cidr      = var.vpc_cidr
  environment   = "dev"
  user_data     = file("${path.module}/user_data.sh.tftpl")
}
