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
  key_path      = "~/.ssh/server.pub"
  vpc_id        = module.network.vpc_id
  vpc_cidr      = var.vpc_cidr
  environment   = "dev"
  user_data     = templatefile("${path.module}/user_data.sh.tftpl", {
    nginx_config = file("${path.module}/nginx.conf")
  })
}

module "storage" {
  source      = "../../modules/storage"
  environment = "dev"
}

module "lambda" {
  source        = "../../modules/lambda"
  environment   = "dev"
  bucket_name   = module.storage.bucket_name
  bucket_arn    = module.storage.bucket_arn
  png_bucket_name = module.storage.png_bucket_name
  png_bucket_arn  = module.storage.png_bucket_arn
  sqs_queue_url = module.storage.sqs_queue_url
  sqs_queue_arn = module.storage.sqs_queue_arn
}
