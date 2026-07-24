terraform {
  required_providers {
    render = {
      source  = "render-oss/render"
      version = "~> 1.0"
    }
  }

  cloud {
    organization = "pedisur"

    workspaces {
      name = "pedisur-infra"
    }
  }
}

provider "render" {
  api_key  = var.render_api_key
  owner_id = var.render_owner_id
}

resource "render_web_service" "backend" {
  name    = "pedisur-backend"
  plan    = "starter"
  region  = "oregon"

  runtime_source = {
    image = {
        image_url = "docker.io/fecu3799/pedisur-backend"
        tag       = "latest"
    }
}

  env_vars = {
    DB_URL                     = { value = var.db_url }
    DB_USER                    = { value = var.db_user }
    DB_PASS                    = { value = var.db_pass, sensitive = true }
    JWT_SECRET                 = { value = var.jwt_secret, sensitive = true }
    JWT_EXPIRATION             = { value = var.jwt_expiration }
    NEW_RELIC_LICENSE_KEY      = { value = var.new_relic_license_key, sensitive = true }
    NEW_RELIC_APP_NAME         = { value = "pedisur-backend" }
    MERCADOPAGO_ACCESS_TOKEN    = { value = var.mercadopago_access_token }
    MERCADOPAGO_WEBHOOK_SECRET = { value = var.mercadopago_webhook_secret }
    R2_ACCESS_KEY              = { value = var.r2_access_key }
    R2_SECRET_KEY              = { value = var.r2_secret_key }
    R2_BUCKET_NAME             = { value = var.r2_bucket_name }
    R2_ENDPOINT                = { value = var.r2_endpoint }
    CORS_ALLOWED_ORIGINS       = { value = var.cors_allowed_origins }
  }
}