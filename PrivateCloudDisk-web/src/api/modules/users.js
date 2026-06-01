import { get, post, patch, put, del } from '@/utils/request';

/**
 * 登录
 * @param {String} phone_number -手机号
 * @param {String} password -密码
 * @returns {Promise}
 */
export function loginApi(phone_number, password) {
    var data = {
        phone_number: phone_number,
        password: password
    };
  return post('/api/v1/business/users/login', data);
}

/**
 * 注册
 * @param {String} phone_number -手机号
 * @param {String} password -密码
 * @param {String} code -验证码
 * @param {String} username -用户名
 * @return {Promise}
 */
export function registerApi(phone_number, password, code, username) {
    let data = {
        phone_number: phone_number,
        password: password,
        code: code,
        name: username
    };
    return post('/api/v1/business/users/', data);
}
/**
 * 获取我的用户信息
 * @returns {Promise}
 */
export function getMyUserInfoApi() {
    return get('/api/v1/business/users/me');
}
/**
 * 更新我的用户信息
 * @param {*} email -邮箱
 * @param {*} username -用户名
 * @param {*} phone_number -手机号
 * @returns {Promise}
 */
export function updateMyUserInfoApi(email, username, phone_number) {
    let data = {
        new_email: email,
        new_username: username,
        new_phone_number: phone_number
    };
    return patch('/api/v1/business/users/me', data);
}
/**
 * 更新用户密码
 * @param {*} old_password 
 * @param {*} new_password 
 * @returns {Promise}
 */
export function changeMyUserPasswordApi(old_password, new_password) {
    let data = {
        old_password: old_password,
        new_password: new_password
    };
    return post('/api/v1/business/users/me/password', data);
}
/**
 * 上传用户头像
 * @param {*} file 
 * @returns {Promise}
 */
export function uploadUserAvatarApi(file) {
    let formData = new FormData();
    formData.append('avatar_file', file);
    return put('/api/v1/business/users/me/avatar', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
/**
 * 获取我账号的在线设备列表
 * @returns {Promise}
 */
export function getMyUserOnlineDevicesApi() {
    return get('/api/v1/business/users/me/online-devices');
}
/**
 * 注销当前用户
 * @returns {Promise}
 */
export function deleteMyUserApi() {
    return del('/api/v1/business/users/me');
}