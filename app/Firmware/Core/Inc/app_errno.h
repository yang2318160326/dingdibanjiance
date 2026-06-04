/**
 * @file    app_errno.h
 * @brief   应用层错误码定义
 */

#ifndef __APP_ERRNO_H
#define __APP_ERRNO_H

typedef enum {
    APP_OK              =  0,
    APP_ERR             = -1,
    APP_ERR_PARAM       = -2,
    APP_ERR_TIMEOUT     = -3,
    APP_ERR_BUSY        = -4,
    APP_ERR_NOMEM       = -5,
    APP_ERR_CRC         = -6,
    APP_ERR_FULL        = -7,
    APP_ERR_EMPTY       = -8,
    APP_ERR_NOT_FOUND   = -9,
    APP_ERR_IO          = -10,
    APP_ERR_PROTO       = -11,
    APP_ERR_PERM        = -12,
} app_err_t;

#endif /* __APP_ERRNO_H */
